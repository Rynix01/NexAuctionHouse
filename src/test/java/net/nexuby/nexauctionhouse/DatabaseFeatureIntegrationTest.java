package net.nexuby.nexauctionhouse;

import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.model.ExpiredItem;
import net.nexuby.nexauctionhouse.model.NotificationSettings;
import net.nexuby.nexauctionhouse.model.PendingRevenue;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseFeatureIntegrationTest extends MockPluginTestSupport {

    @Test
    void createsEveryRequiredSqliteTableAndMigrationColumn() throws Exception {
        String[] tables = {
                "auctions", "expired_items", "transaction_logs", "pending_revenue",
                "rescued_items", "bids", "favorites", "player_settings"
        };
        for (String table : tables) {
            try (ResultSet rs = plugin.getDatabaseManager().getConnection().getMetaData()
                    .getTables(null, null, table, new String[]{"TABLE"})) {
                assertTrue(rs.next(), "Missing table: " + table);
            }
        }

        boolean hasTheme = false;
        try (ResultSet rs = plugin.getDatabaseManager().getConnection().getMetaData()
                .getColumns(null, null, "player_settings", "theme")) {
            hasTheme = rs.next();
        }
        assertTrue(hasTheme);
    }

    @Test
    void pendingRevenueLeaseIsOwnerAndTokenBound() {
        AuctionDAO dao = plugin.getAuctionManager().getDao();
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        dao.insertPendingRevenue(owner, "Owner", 42.5, "money", 7, "Diamond", "Buyer");
        PendingRevenue revenue = dao.getPendingRevenue(owner).getFirst();
        long now = System.currentTimeMillis();

        assertFalse(dao.claimPendingRevenue(revenue.getId(), attacker, "attacker", now, now - 60_000));
        assertTrue(dao.claimPendingRevenue(revenue.getId(), owner, "token-a", now, now - 60_000));
        assertFalse(dao.claimPendingRevenue(revenue.getId(), owner, "token-b", now, now - 60_000));
        assertFalse(dao.acknowledgePendingRevenue(revenue.getId(), "wrong"));
        assertTrue(dao.releasePendingRevenue(revenue.getId(), "token-a"));
        assertTrue(dao.claimPendingRevenue(revenue.getId(), owner, "token-b", now + 1, now - 60_000));
        assertTrue(dao.acknowledgePendingRevenue(revenue.getId(), "token-b"));
        assertTrue(dao.getPendingRevenue(owner).isEmpty());
    }

    @Test
    void expiredItemCanBeClaimedOnlyOnceAndOnlyByOwner() {
        AuctionDAO dao = plugin.getAuctionManager().getDao();
        UUID owner = UUID.randomUUID();
        assertTrue(dao.insertExpiredItem(owner, "Owner", new ItemStack(Material.DIAMOND, 4), "EXPIRED"));
        ExpiredItem item = dao.getExpiredItems(owner).getFirst();

        assertFalse(dao.claimExpiredItem(item.getId(), UUID.randomUUID()));
        assertTrue(dao.claimExpiredItem(item.getId(), owner));
        assertFalse(dao.claimExpiredItem(item.getId(), owner));
        assertTrue(dao.getExpiredItems(owner).isEmpty());
    }

    @Test
    void notificationSettingsRoundTripAndUpsert() {
        AuctionDAO dao = plugin.getAuctionManager().getDao();
        UUID player = UUID.randomUUID();
        NotificationSettings settings = new NotificationSettings(player, true, false, true, false, true);
        dao.saveNotificationSettings(settings);

        NotificationSettings loaded = dao.getNotificationSettings(player);
        assertNotNull(loaded);
        assertTrue(loaded.isSaleNotifications());
        assertFalse(loaded.isBidNotifications());
        assertFalse(loaded.isLoginNotifications());

        settings.toggle("sale");
        settings.toggle("bid");
        dao.saveNotificationSettings(settings);
        loaded = dao.getNotificationSettings(player);
        assertFalse(loaded.isSaleNotifications());
        assertTrue(loaded.isBidNotifications());
    }

    @Test
    void playerThemeRoundTripsWithoutOverwritingSettings() {
        AuctionDAO dao = plugin.getAuctionManager().getDao();
        UUID player = UUID.randomUUID();
        NotificationSettings settings = new NotificationSettings(player, false, true, false, true, false);
        dao.saveNotificationSettings(settings);

        dao.setPlayerTheme(player, "dark");

        assertEquals("dark", dao.getPlayerTheme(player));
        NotificationSettings loaded = dao.getNotificationSettings(player);
        assertFalse(loaded.isSaleNotifications());
        assertTrue(loaded.isBidNotifications());
        assertFalse(loaded.isSoundEffects());
    }

    @Test
    void transactionHistoryAndStatisticsUseOnlyCompletedSales() {
        AuctionDAO dao = plugin.getAuctionManager().getDao();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        dao.logTransaction(1, seller, null, diamond, 90, 0, "LIST");
        dao.logTransaction(1, seller, buyer, diamond, 100, 10, "SALE");
        dao.logTransaction(2, seller, buyer, diamond, 200, 20, "AUCTION_COMPLETE");

        assertEquals(2, dao.getPlayerHistory(seller, 10).size());
        assertEquals(2, dao.getPlayerTotalSales(seller));
        assertEquals(270, dao.getPlayerTotalRevenue(seller), 0.001);
        assertEquals(2, dao.getPlayerTotalPurchases(buyer));
        assertEquals(150, dao.getAveragePrice("diamond", 7), 0.001);
        assertEquals(150, dao.getAllAveragePrices(7).get("DIAMOND"), 0.001);
        assertEquals(2, dao.getSaleCountSince(System.currentTimeMillis() - 60_000));
        assertEquals(300, dao.getTotalVolumeSince(System.currentTimeMillis() - 60_000), 0.001);
        assertEquals(200, dao.getMostExpensiveSale().getPrice(), 0.001);
        List<String[]> top = dao.getTopSellers(5);
        assertEquals(1, top.size());
        assertEquals("2", top.getFirst()[1]);
    }
}
