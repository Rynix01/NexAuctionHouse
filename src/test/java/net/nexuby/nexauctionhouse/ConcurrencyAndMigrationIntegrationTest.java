package net.nexuby.nexauctionhouse;

import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.migration.AbstractMigrator;
import net.nexuby.nexauctionhouse.migration.MigrationReport;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionStatus;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyAndMigrationIntegrationTest extends MockPluginTestSupport {

    @Test
    void onlyOneConcurrentBuyerCanClaimAuctionRow() throws Exception {
        PlayerMock seller = server.addPlayer("Seller");
        int id = plugin.getAuctionManager().listItem(seller, new ItemStack(Material.DIAMOND), 100, "money");
        AuctionDAO dao = plugin.getAuctionManager().getDao();
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return dao.transitionAuctionStatus(id, AuctionStatus.ACTIVE, AuctionStatus.PROCESSING);
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return dao.transitionAuctionStatus(id, AuctionStatus.ACTIVE, AuctionStatus.PROCESSING);
            });
            start.countDown();

            int winners = (first.get(5, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, winners);
            assertEquals(AuctionStatus.PROCESSING, dao.getAuctionById(id).getStatus());
        }
    }

    @Test
    void onlyOneConcurrentBidCompareAndSetWins() throws Exception {
        PlayerMock seller = server.addPlayer("Seller");
        int id = plugin.getAuctionManager().listBidItem(seller, new ItemStack(Material.EMERALD), 100, "money");
        AuctionDAO dao = plugin.getAuctionManager().getDao();
        UUID firstBidder = UUID.randomUUID();
        UUID secondBidder = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return dao.compareAndSetHighestBid(id, 0, null, 100, firstBidder, "First");
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return dao.compareAndSetHighestBid(id, 0, null, 110, secondBidder, "Second");
            });
            start.countDown();

            int winners = (first.get(5, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, winners);
            AuctionItem persisted = dao.getAuctionById(id);
            assertTrue(persisted.getHighestBid() == 100 || persisted.getHighestBid() == 110);
            assertNotNull(persisted.getHighestBidderUuid());
        }
    }

    @Test
    void migrationPausesAllMarketWritesUntilItFinishes() throws Exception {
        PlayerMock seller = server.addPlayer("Seller");
        CountDownLatch migrationStarted = new CountDownLatch(1);
        CountDownLatch releaseMigration = new CountDownLatch(1);
        AbstractMigrator migrator = new AbstractMigrator(plugin, "TestSource") {
            @Override
            public String getSourceName() {
                return "TestSource";
            }

            @Override
            public String validate() {
                return null;
            }

            @Override
            public boolean createBackup() {
                return true;
            }

            @Override
            public MigrationReport migrate() {
                migrationStarted.countDown();
                try {
                    assertTrue(releaseMigration.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail(e);
                }
                report.finish();
                return report;
            }
        };

        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<MigrationReport> migration = executor.submit(() ->
                    plugin.getMigrationManager().executeMigration(migrator));
            assertTrue(migrationStarted.await(5, TimeUnit.SECONDS));
            assertTrue(plugin.getMigrationManager().isMigrationInProgress());
            assertEquals(-1, plugin.getAuctionManager().listItem(
                    seller, new ItemStack(Material.DIAMOND), 100, "money"));

            releaseMigration.countDown();
            assertNotNull(migration.get(5, TimeUnit.SECONDS));
            assertFalse(plugin.getMigrationManager().isMigrationInProgress());
            assertTrue(plugin.getAuctionManager().listItem(
                    seller, new ItemStack(Material.DIAMOND), 100, "money") > 0);
        }
    }
}
