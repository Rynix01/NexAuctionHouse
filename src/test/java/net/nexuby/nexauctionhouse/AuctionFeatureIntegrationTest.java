package net.nexuby.nexauctionhouse;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuctionFeatureIntegrationTest extends MockPluginTestSupport {

    @Test
    void listsAndCancelsBinAuctionAndReturnsItem() {
        PlayerMock seller = server.addPlayer("Seller");
        AuctionManager manager = plugin.getAuctionManager();

        int id = manager.listItem(seller, new ItemStack(Material.DIAMOND, 3), 100, "money");

        assertTrue(id > 0);
        assertEquals(1, manager.getPlayerActiveListings(seller.getUniqueId()));
        assertEquals(3, manager.getAuction(id).getItemStack().getAmount());
        assertTrue(manager.cancelAuction(seller, id, false));
        assertNull(manager.getAuction(id));
        assertTrue(seller.getInventory().contains(Material.DIAMOND, 3));
    }

    @Test
    void purchasesItemAndSettlesTaxedPayment() {
        PlayerMock seller = server.addPlayer("Seller");
        PlayerMock buyer = server.addPlayer("Buyer");
        setBalance(seller, 0);
        setBalance(buyer, 1_000);

        int id = plugin.getAuctionManager().listItem(seller, new ItemStack(Material.EMERALD, 2), 200, "money");

        assertTrue(plugin.getAuctionManager().purchaseItem(buyer, id));
        assertEquals(800, balanceOf(buyer), 0.001);
        assertEquals(180, balanceOf(seller), 0.001);
        assertTrue(buyer.getInventory().contains(Material.EMERALD, 2));
        assertNull(plugin.getAuctionManager().getAuction(id));
        assertEquals(1, plugin.getAuctionManager().getDao().getPlayerTotalSales(seller.getUniqueId()));
        assertEquals(1, plugin.getAuctionManager().getDao().getPlayerTotalPurchases(buyer.getUniqueId()));
    }

    @Test
    void rejectsOwnPurchaseAndInsufficientBalance() {
        PlayerMock seller = server.addPlayer("Seller");
        PlayerMock buyer = server.addPlayer("Buyer");
        int id = plugin.getAuctionManager().listItem(seller, new ItemStack(Material.GOLD_INGOT), 100, "money");

        assertFalse(plugin.getAuctionManager().purchaseItem(seller, id));
        assertFalse(plugin.getAuctionManager().purchaseItem(buyer, id));
        assertNotNull(plugin.getAuctionManager().getAuction(id));
    }

    @Test
    void bidFlowWithdrawsNewBidAndRefundsPreviousBidder() {
        PlayerMock seller = server.addPlayer("Seller");
        PlayerMock first = server.addPlayer("FirstBidder");
        PlayerMock second = server.addPlayer("SecondBidder");
        setBalance(first, 1_000);
        setBalance(second, 1_000);

        int id = plugin.getAuctionManager().listBidItem(seller, new ItemStack(Material.NETHERITE_INGOT), 100, "money");

        assertTrue(plugin.getAuctionManager().placeBid(first, id, 100));
        assertEquals(900, balanceOf(first), 0.001);
        assertFalse(plugin.getAuctionManager().placeBid(first, id, 110));
        assertFalse(plugin.getAuctionManager().placeBid(second, id, 104.99));
        assertTrue(plugin.getAuctionManager().placeBid(second, id, 105));
        assertEquals(1_000, balanceOf(first), 0.001);
        assertEquals(895, balanceOf(second), 0.001);
        assertEquals(second.getUniqueId(), plugin.getAuctionManager().getAuction(id).getHighestBidderUuid());
        assertEquals(105, plugin.getAuctionManager().getAuction(id).getCurrentPrice(), 0.001);
        assertEquals(2, plugin.getAuctionManager().getDao().getBidsByAuction(id).size());
    }

    @Test
    void sellerCannotCancelBidAuctionWithActiveBidButAdminCan() {
        PlayerMock seller = server.addPlayer("Seller");
        PlayerMock bidder = server.addPlayer("Bidder");
        setBalance(bidder, 500);
        int id = plugin.getAuctionManager().listBidItem(seller, new ItemStack(Material.DIAMOND_SWORD), 100, "money");
        assertTrue(plugin.getAuctionManager().placeBid(bidder, id, 100));

        assertFalse(plugin.getAuctionManager().cancelAuction(seller, id, false));
        assertTrue(plugin.getAuctionManager().cancelAuction(seller, id, true));
        assertEquals(500, balanceOf(bidder), 0.001);
        assertTrue(seller.getInventory().contains(Material.DIAMOND_SWORD));
    }

    @Test
    void bundlePurchaseDeliversEveryItem() {
        PlayerMock seller = server.addPlayer("Seller");
        PlayerMock buyer = server.addPlayer("Buyer");
        setBalance(buyer, 500);
        List<ItemStack> items = List.of(
                new ItemStack(Material.DIAMOND, 2),
                new ItemStack(Material.EMERALD, 4),
                new ItemStack(Material.GOLD_INGOT, 6)
        );

        int id = plugin.getAuctionManager().listBundle(seller, items, 300, "money");

        assertTrue(plugin.getAuctionManager().getAuction(id).isBundle());
        assertTrue(plugin.getAuctionManager().purchaseItem(buyer, id));
        assertTrue(buyer.getInventory().contains(Material.DIAMOND, 2));
        assertTrue(buyer.getInventory().contains(Material.EMERALD, 4));
        assertTrue(buyer.getInventory().contains(Material.GOLD_INGOT, 6));
    }

    @Test
    void validatesAmountsCurrencyAndAuctionType() {
        PlayerMock seller = server.addPlayer("Seller");
        ItemStack item = new ItemStack(Material.STONE);

        assertEquals(-1, plugin.getAuctionManager().listItem(seller, item, Double.NaN, "money"));
        assertEquals(-1, plugin.getAuctionManager().listItem(seller, item, Double.POSITIVE_INFINITY, "money"));
        assertEquals(-1, plugin.getAuctionManager().listItem(seller, item, -1, "money"));
        assertEquals(-1, plugin.getAuctionManager().listItem(seller, item, 100, "unknown"));

        int bidId = plugin.getAuctionManager().listBidItem(seller, item, 100, "money");
        PlayerMock buyer = server.addPlayer("Buyer");
        setBalance(buyer, 500);
        assertFalse(plugin.getAuctionManager().purchaseItem(buyer, bidId));
    }

    @Test
    void updatesPriceAndExtendsDurationOnlyForOwner() {
        PlayerMock seller = server.addPlayer("Seller");
        PlayerMock other = server.addPlayer("Other");
        int id = plugin.getAuctionManager().listItem(seller, new ItemStack(Material.IRON_INGOT), 100, "money");
        long oldExpiry = plugin.getAuctionManager().getAuction(id).getExpiresAt();

        assertFalse(plugin.getAuctionManager().updatePrice(other, id, 125));
        assertTrue(plugin.getAuctionManager().updatePrice(seller, id, 125));
        assertEquals(125, plugin.getAuctionManager().getAuction(id).getPrice(), 0.001);
        assertFalse(plugin.getAuctionManager().extendDuration(other, id, 1));
        assertTrue(plugin.getAuctionManager().extendDuration(seller, id, 1));
        assertTrue(plugin.getAuctionManager().getAuction(id).getExpiresAt() > oldExpiry);
    }

    @Test
    void supportsFavoritesAndCleansThemWhenAuctionEnds() {
        PlayerMock seller = server.addPlayer("Seller");
        PlayerMock watcher = server.addPlayer("Watcher");
        int id = plugin.getAuctionManager().listItem(seller, new ItemStack(Material.BOOK), 20, "money");

        assertTrue(plugin.getAuctionManager().addFavorite(watcher, id));
        assertTrue(plugin.getAuctionManager().isFavorited(watcher.getUniqueId(), id));
        assertEquals(1, plugin.getAuctionManager().getFavoriteAuctions(watcher.getUniqueId()).size());
        assertTrue(plugin.getAuctionManager().cancelAuction(seller, id, false));
        assertFalse(plugin.getAuctionManager().isFavorited(watcher.getUniqueId(), id));
    }

    @Test
    void checksMaterialLorePersistentDataAndWhitelistRules() {
        AuctionManager manager = plugin.getAuctionManager();
        assertTrue(manager.isBlacklisted(new ItemStack(Material.BEDROCK)));
        assertFalse(manager.isBlacklisted(new ItemStack(Material.DIAMOND)));

        ItemStack loreItem = new ItemStack(Material.PAPER);
        ItemMeta loreMeta = loreItem.getItemMeta();
        loreMeta.lore(List.of(Component.text("This item is Soulbound")));
        loreItem.setItemMeta(loreMeta);
        assertTrue(manager.isBlacklisted(loreItem));

        plugin.getConfigManager().getConfig().set("blacklist.nbt-tags", List.of("test:locked"));
        ItemStack tagged = new ItemStack(Material.STICK);
        ItemMeta taggedMeta = tagged.getItemMeta();
        taggedMeta.getPersistentDataContainer().set(new NamespacedKey("test", "locked"), PersistentDataType.BYTE, (byte) 1);
        tagged.setItemMeta(taggedMeta);
        assertTrue(manager.isBlacklisted(tagged));

        plugin.getConfigManager().getConfig().set("blacklist.mode", "whitelist");
        plugin.getConfigManager().getConfig().set("blacklist.whitelist-materials", List.of("DIAMOND"));
        assertFalse(manager.isBlacklisted(new ItemStack(Material.DIAMOND)));
        assertTrue(manager.isBlacklisted(new ItemStack(Material.EMERALD)));
    }

    @Test
    void snapshotsCannotMutateCachedAuctionOrBundle() {
        PlayerMock seller = server.addPlayer("Seller");
        int id = plugin.getAuctionManager().listBundle(seller,
                List.of(new ItemStack(Material.DIAMOND), new ItemStack(Material.EMERALD)), 50, "money");

        AuctionItem snapshot = plugin.getAuctionManager().getAuction(id);
        snapshot.setPrice(999);
        snapshot.getBundleItems().get(0).setType(Material.DIRT);

        AuctionItem cached = plugin.getAuctionManager().getAuction(id);
        assertEquals(50, cached.getPrice(), 0.001);
        assertEquals(Material.DIAMOND, cached.getBundleItems().get(0).getType());
        assertEquals(AuctionType.BIN, cached.getAuctionType());
    }
}
