package net.nexuby.nexauctionhouse;

import net.nexuby.nexauctionhouse.api.NexAuctionHouseAPI;
import net.nexuby.nexauctionhouse.api.PlayerStats;
import net.nexuby.nexauctionhouse.api.event.AuctionListEvent;
import net.nexuby.nexauctionhouse.api.event.AuctionPurchaseEvent;
import net.nexuby.nexauctionhouse.api.event.BidPlaceEvent;
import net.nexuby.nexauctionhouse.manager.CursorProtectionManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock;

import static org.junit.jupiter.api.Assertions.*;

class ApiAndListenerIntegrationTest extends MockPluginTestSupport {

    @Test
    void developerApiReadsCreatesAndRemovesAuctions() {
        PlayerMock seller = server.addPlayer("Seller");
        NexAuctionHouseAPI api = NexAuctionHouseAPI.getInstance();
        assertNotNull(api);
        assertEquals("1.0.0", api.getVersion());

        int id = api.forceCreateAuction(seller, new ItemStack(Material.DIAMOND), 100, "money");

        assertTrue(id > 0);
        assertEquals(1, api.getActiveAuctions().size());
        assertEquals(1, api.getAuctionsByPlayer(seller.getUniqueId()).size());
        assertNotNull(api.getAuction(id));
        assertTrue(api.isBlacklisted(new ItemStack(Material.BEDROCK)));
        PlayerStats stats = api.getPlayerStats(seller.getUniqueId());
        assertEquals(1, stats.getActiveListings());
        assertEquals(seller.getUniqueId(), stats.getPlayerUuid());
        assertTrue(api.forceRemoveAuction(id));
        assertNull(api.getAuction(id));
    }

    @Test
    void cancellableListEventPreventsDatabaseWrite() {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void cancel(AuctionListEvent event) {
                event.setCancelled(true);
            }
        }, plugin);
        PlayerMock seller = server.addPlayer("Seller");

        assertEquals(-1, plugin.getAuctionManager().listItem(
                seller, new ItemStack(Material.EMERALD), 50, "money"));
        assertTrue(plugin.getAuctionManager().getActiveAuctionsList().isEmpty());
    }

    @Test
    void cancellablePurchaseEventPreventsSettlement() {
        PlayerMock seller = server.addPlayer("Seller");
        PlayerMock buyer = server.addPlayer("Buyer");
        setBalance(buyer, 500);
        int id = plugin.getAuctionManager().listItem(seller, new ItemStack(Material.GOLD_INGOT), 100, "money");
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void cancel(AuctionPurchaseEvent event) {
                event.setCancelled(true);
            }
        }, plugin);

        assertFalse(plugin.getAuctionManager().purchaseItem(buyer, id));
        assertEquals(500, balanceOf(buyer), 0.001);
        assertNotNull(plugin.getAuctionManager().getAuction(id));
        assertFalse(buyer.getInventory().contains(Material.GOLD_INGOT));
    }

    @Test
    void cancellableBidEventPreventsWithdrawal() {
        PlayerMock seller = server.addPlayer("Seller");
        PlayerMock bidder = server.addPlayer("Bidder");
        setBalance(bidder, 500);
        int id = plugin.getAuctionManager().listBidItem(seller, new ItemStack(Material.BOOK), 100, "money");
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void cancel(BidPlaceEvent event) {
                event.setCancelled(true);
            }
        }, plugin);

        assertFalse(plugin.getAuctionManager().placeBid(bidder, id, 100));
        assertEquals(500, balanceOf(bidder), 0.001);
        assertEquals(0, plugin.getAuctionManager().getAuction(id).getHighestBid(), 0.001);
    }

    @Test
    void reconnectDeliversPendingRevenueExactlyOnce() {
        PlayerMock player = server.addPlayer("OfflineSeller");
        plugin.getAuctionManager().getDao().insertPendingRevenue(
                player.getUniqueId(), player.getName(), 125, "money", 9, "Diamond", "Buyer");
        assertTrue(player.disconnect());
        assertTrue(player.reconnect());

        drainJoinTasks();

        assertEquals(125, balanceOf(player), 0.001);
        assertTrue(plugin.getAuctionManager().getDao().getPendingRevenue(player.getUniqueId()).isEmpty());
        drainJoinTasks();
        assertEquals(125, balanceOf(player), 0.001);
    }

    @Test
    void reconnectRestoresCrashProtectedItemExactlyOnce() {
        PlayerMock player = server.addPlayer("RecoveredPlayer");
        plugin.getCursorProtectionManager().saveRescuedItem(
                player.getUniqueId(), player.getName(), new ItemStack(Material.NETHERITE_INGOT), "CRASH");
        assertTrue(player.disconnect());
        assertTrue(player.reconnect());

        drainJoinTasks();

        assertTrue(player.getInventory().contains(Material.NETHERITE_INGOT));
        assertTrue(plugin.getCursorProtectionManager().getRescuedItems(player.getUniqueId()).isEmpty());
    }

    private void drainJoinTasks() {
        BukkitSchedulerMock scheduler = server.getScheduler();
        scheduler.performTicks(40);
        scheduler.waitAsyncTasksFinished();
        scheduler.performOneTick();
        scheduler.waitAsyncTasksFinished();
        scheduler.performOneTick();
        scheduler.waitAsyncTasksFinished();
    }
}
