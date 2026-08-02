package net.nexuby.nexauctionhouse;

import com.mongodb.client.MongoCollection;
import net.nexuby.nexauctionhouse.database.MongoAuctionDAO;
import net.nexuby.nexauctionhouse.database.MongoManager;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionStatus;
import net.nexuby.nexauctionhouse.model.AuctionType;
import net.nexuby.nexauctionhouse.model.ExpiredItem;
import net.nexuby.nexauctionhouse.model.NotificationSettings;
import net.nexuby.nexauctionhouse.model.PendingRevenue;
import net.nexuby.nexauctionhouse.redis.RedisManager;
import net.nexuby.nexauctionhouse.redis.SignedMessageCodec;
import org.bson.Document;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("external")
class ExternalServicesIntegrationTest extends MockPluginTestSupport {

    private static final String REDIS_SECRET = "nexah-external-test-secret-32-characters-minimum";

    private MongoManager mongo;
    private RedisManager redisA;
    private RedisManager redisB;
    private String mongoDatabase;
    private String redisPrefix;
    private String redisHost;
    private int redisPort;
    private int redisDatabase;

    @BeforeEach
    void configureExternalServices() {
        mongoDatabase = "nexah_it_" + UUID.randomUUID().toString().replace("-", "");
        redisPrefix = "nexah-it-" + UUID.randomUUID();
        redisHost = System.getProperty("nexah.redis.host", "127.0.0.1");
        redisPort = Integer.parseInt(System.getProperty("nexah.redis.port", "6379"));
        redisDatabase = 15;

        plugin.getConfig().set("database.mongodb.connection-string",
                System.getProperty("nexah.mongo.uri", "mongodb://127.0.0.1:27018"));
        plugin.getConfig().set("database.mongodb.database", mongoDatabase);
        plugin.getConfig().set("cross-server.server-id", "integration-node");
        plugin.getConfig().set("cross-server.redis.host", redisHost);
        plugin.getConfig().set("cross-server.redis.port", redisPort);
        plugin.getConfig().set("cross-server.redis.password",
                System.getProperty("nexah.redis.password", ""));
        plugin.getConfig().set("cross-server.redis.database", redisDatabase);
        plugin.getConfig().set("cross-server.redis.timeout", 3000);
        plugin.getConfig().set("cross-server.redis.max-pool-size", 4);
        plugin.getConfig().set("cross-server.redis.channel-prefix", redisPrefix);
        plugin.getConfig().set("cross-server.redis.message-secret", REDIS_SECRET);
        plugin.getConfig().set("cross-server.redis.use-ssl", false);
    }

    @AfterEach
    void cleanExternalServices() {
        if (redisB != null) redisB.disconnect();
        if (redisA != null) redisA.disconnect();
        if (mongo != null) {
            if (mongo.getDatabase() != null) mongo.getDatabase().drop();
            mongo.disconnect();
        }
    }

    @Test
    void mongoConnectsCreatesIndexesAndAllocatesUniqueIdsConcurrently() throws Exception {
        connectMongo();
        assertTrue(hasIndex(mongo.favorites(), "player_uuid_1_auction_id_1"));

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            Set<Future<Integer>> futures = new HashSet<>();
            for (int i = 0; i < 64; i++) futures.add(executor.submit(() -> mongo.getNextId("concurrent")));
            Set<Integer> ids = new HashSet<>();
            for (Future<Integer> future : futures) ids.add(future.get(5, TimeUnit.SECONDS));
            assertEquals(64, ids.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void mongoAuctionBidAndFavoriteLifecycleMatchesDaoContract() {
        MongoAuctionDAO dao = connectMongoDao();
        UUID seller = UUID.randomUUID();
        UUID bidderA = UUID.randomUUID();
        UUID bidderB = UUID.randomUUID();

        int id = dao.insertAuction(auction(seller));
        assertTrue(id > 0);
        assertEquals(100.0, dao.getAuctionById(id).getPrice());
        assertEquals(1, dao.getActiveAuctions().size());
        assertEquals(1, dao.getAuctionsBySeller(seller).size());

        assertTrue(dao.compareAndSetHighestBid(id, 0, null, 120, bidderA, "BidderA"));
        assertFalse(dao.compareAndSetHighestBid(id, 0, null, 130, bidderB, "BidderB"));
        assertTrue(dao.compareAndSetHighestBid(id, 120, bidderA, 130, bidderB, "BidderB"));
        assertTrue(dao.insertBid(id, bidderA, "BidderA", 120) > 0);
        assertTrue(dao.insertBid(id, bidderB, "BidderB", 130) > 0);
        assertEquals(bidderB, dao.getHighestBid(id).getBidderUuid());
        assertEquals(2, dao.getUniqueBiddersByAuction(id).size());

        UUID watcher = UUID.randomUUID();
        assertTrue(dao.addFavorite(watcher, id));
        assertFalse(dao.addFavorite(watcher, id));
        assertTrue(dao.isFavorited(watcher, id));
        assertEquals(1, dao.getFavoriteCount(watcher));
        assertEquals(watcher, dao.getPlayersWhoFavorited(id).getFirst());
        assertTrue(dao.removeFavorite(watcher, id));
        assertTrue(dao.transitionAuctionStatus(id, AuctionStatus.ACTIVE, AuctionStatus.SOLD));
        assertFalse(dao.transitionAuctionStatus(id, AuctionStatus.ACTIVE, AuctionStatus.CANCELLED));
    }

    @Test
    void mongoClaimsRevenueAndExpiredItemsOnlyForTheirOwner() {
        MongoAuctionDAO dao = connectMongoDao();
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        dao.insertPendingRevenue(owner, "Owner", 42.5, "money", 7, "Diamond", "Buyer");
        PendingRevenue revenue = dao.getPendingRevenue(owner).getFirst();
        long now = System.currentTimeMillis();

        assertFalse(dao.claimPendingRevenue(revenue.getId(), attacker, "attacker", now, now - 60_000));
        assertTrue(dao.claimPendingRevenue(revenue.getId(), owner, "token-a", now, now - 60_000));
        assertFalse(dao.acknowledgePendingRevenue(revenue.getId(), "wrong"));
        assertTrue(dao.releasePendingRevenue(revenue.getId(), "token-a"));
        assertTrue(dao.claimPendingRevenue(revenue.getId(), owner, "token-b", now + 1, now - 60_000));
        assertTrue(dao.acknowledgePendingRevenue(revenue.getId(), "token-b"));

        assertTrue(dao.insertExpiredItem(owner, "Owner", new ItemStack(Material.DIAMOND, 4), "EXPIRED"));
        ExpiredItem expired = dao.getExpiredItems(owner).getFirst();
        assertFalse(dao.claimExpiredItem(expired.getId(), attacker));
        assertTrue(dao.claimExpiredItem(expired.getId(), owner));
        assertFalse(dao.claimExpiredItem(expired.getId(), owner));
    }

    @Test
    void mongoSettingsAndCompletedSaleStatisticsRoundTrip() {
        MongoAuctionDAO dao = connectMongoDao();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        NotificationSettings settings = new NotificationSettings(seller, true, false, true, false, true);
        dao.saveNotificationSettings(settings);
        dao.setPlayerTheme(seller, "dark");
        assertEquals("dark", dao.getPlayerTheme(seller));
        assertFalse(dao.getNotificationSettings(seller).isBidNotifications());

        ItemStack diamond = new ItemStack(Material.DIAMOND);
        dao.logTransaction(1, seller, null, diamond, 90, 0, "LIST");
        dao.logTransaction(1, seller, buyer, diamond, 100, 10, "SALE");
        dao.logTransaction(2, seller, buyer, diamond, 200, 20, "AUCTION_COMPLETE");
        assertEquals(2, dao.getPlayerTotalSales(seller));
        assertEquals(270, dao.getPlayerTotalRevenue(seller), 0.001);
        assertEquals(2, dao.getPlayerTotalPurchases(buyer));
        assertEquals(150, dao.getAveragePrice("diamond", 7), 0.001);
        assertEquals(300, dao.getTotalVolumeSince(System.currentTimeMillis() - 60_000), 0.001);
        assertEquals(200, dao.getMostExpensiveSale().getPrice(), 0.001);
    }

    @Test
    void redisConnectsAndReportsHealth() {
        redisA = connectRedis();
        assertTrue(redisA.isConnected());
    }

    @Test
    void redisPubSubAcceptsSignedMessagesAndRejectsTamperingAndReplay() throws Exception {
        redisA = connectRedis();
        AtomicInteger deliveries = new AtomicInteger();
        CountDownLatch validDelivery = new CountDownLatch(1);
        redisA.subscribe("contract", (channel, payload) -> {
            deliveries.incrementAndGet();
            if ("valid".equals(payload)) validDelivery.countDown();
        });

        SignedMessageCodec codec = new SignedMessageCodec(REDIS_SECRET);
        String fullChannel = redisPrefix + ":contract";
        try (Jedis publisher = jedis()) {
            publisher.publish(fullChannel, "unsigned-tampered-payload");
            String signed = codec.sign("valid");
            for (int i = 0; i < 20 && validDelivery.getCount() > 0; i++) {
                publisher.publish(fullChannel, signed);
                Thread.sleep(50);
            }
            assertTrue(validDelivery.await(2, TimeUnit.SECONDS));
            publisher.publish(fullChannel, signed);
        }
        Thread.sleep(150);
        assertEquals(1, deliveries.get());
    }

    @Test
    void redisDistributedLockCannotBeReleasedByNonOwner() {
        redisA = connectRedis();
        redisB = connectRedis();

        assertTrue(redisA.tryLock("auction:42", 10));
        assertFalse(redisB.tryLock("auction:42", 10));
        redisB.releaseLock("auction:42");
        assertFalse(redisB.tryLock("auction:42", 10), "A non-owner must not release another node's lock");
        redisA.releaseLock("auction:42");
        assertTrue(redisB.tryLock("auction:42", 10));
        redisB.releaseLock("auction:42");
    }

    @Test
    void redisRejectsShortMessageSecretBeforeOpeningPool() {
        plugin.getConfig().set("cross-server.redis.message-secret", "too-short");
        redisA = new RedisManager(plugin);
        assertFalse(redisA.connect());
        assertFalse(redisA.isConnected());
    }

    private void connectMongo() {
        mongo = new MongoManager(plugin);
        assertTimeoutPreemptively(Duration.ofSeconds(8), () -> assertTrue(mongo.connect()));
    }

    private MongoAuctionDAO connectMongoDao() {
        connectMongo();
        return new MongoAuctionDAO(plugin, mongo);
    }

    private RedisManager connectRedis() {
        RedisManager manager = new RedisManager(plugin);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertTrue(manager.connect()));
        return manager;
    }

    private Jedis jedis() {
        Jedis jedis = new Jedis(redisHost, redisPort);
        String password = System.getProperty("nexah.redis.password", "");
        if (!password.isEmpty()) jedis.auth(password);
        jedis.select(redisDatabase);
        return jedis;
    }

    private AuctionItem auction(UUID seller) {
        long now = System.currentTimeMillis();
        return new AuctionItem(0, seller, "Seller", new ItemStack(Material.DIAMOND, 3),
                100, "money", 5, now, now + 60_000, AuctionStatus.ACTIVE,
                AuctionType.AUCTION, 0, null, null);
    }

    private boolean hasIndex(MongoCollection<Document> collection, String name) {
        for (Document index : collection.listIndexes()) {
            if (name.equals(index.getString("name"))) return true;
        }
        return false;
    }
}
