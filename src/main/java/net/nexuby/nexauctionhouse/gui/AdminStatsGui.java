package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.TransactionLog;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin statistics GUI showing server-wide market stats:
 * - Total sales (24h), daily volume, most expensive sale
 * - Top 5 sellers with their sale counts and total value
 */
public class AdminStatsGui extends AbstractGui {

    public AdminStatsGui(NexAuctionHouse plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected void build() {
        inventory = Bukkit.createInventory(this, 54,
                text("<dark_red>Market Statistics"));

        AuctionDAO dao = plugin.getAuctionManager().getDao();
        AuctionManager manager = plugin.getAuctionManager();

        long now = System.currentTimeMillis();
        long last24h = now - 86400000L;
        long last7d = now - 604800000L;

        // -- Overview Item (slot 4) --
        int sales24h = dao.getSaleCountSince(last24h);
        double volume24h = dao.getTotalVolumeSince(last24h);
        int sales7d = dao.getSaleCountSince(last7d);
        double volume7d = dao.getTotalVolumeSince(last7d);
        int activeListings = manager.getActiveAuctionsList().size();

        ItemStack overview = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta overviewMeta = overview.getItemMeta();
        overviewMeta.displayName(text("<gold>Market Overview"));
        List<Component> overviewLore = new ArrayList<>();
        overviewLore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
        overviewLore.add(text("<gray>Active Listings: <white>" + activeListings));
        overviewLore.add(Component.empty());
        overviewLore.add(text("<yellow>Last 24 Hours:"));
        overviewLore.add(text("<gray>  Sales: <green>" + sales24h));
        overviewLore.add(text("<gray>  Volume: <green>" + plugin.getEconomyManager().format(volume24h)));
        overviewLore.add(Component.empty());
        overviewLore.add(text("<yellow>Last 7 Days:"));
        overviewLore.add(text("<gray>  Sales: <green>" + sales7d));
        overviewLore.add(text("<gray>  Volume: <green>" + plugin.getEconomyManager().format(volume7d)));
        overviewLore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
        overviewMeta.lore(overviewLore);
        overview.setItemMeta(overviewMeta);
        inventory.setItem(4, overview);

        // -- Most Expensive Sale (slot 22) --
        TransactionLog expensive = dao.getMostExpensiveSale();
        if (expensive != null) {
            ItemStack expItem = expensive.getItemStack().clone();
            ItemMeta expMeta = expItem.getItemMeta();
            expMeta.displayName(text("<light_purple>Most Expensive Sale"));

            String sellerName = Bukkit.getOfflinePlayer(expensive.getSellerUuid()).getName();
            String buyerName = expensive.getBuyerUuid() != null
                    ? Bukkit.getOfflinePlayer(expensive.getBuyerUuid()).getName() : "Unknown";
            if (sellerName == null) sellerName = "Unknown";
            if (buyerName == null) buyerName = "Unknown";

            List<Component> expLore = new ArrayList<>();
            expLore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
            expLore.add(text("<gray>Price: <green>" + plugin.getEconomyManager().format(expensive.getPrice())));
            expLore.add(text("<gray>Seller: <white>" + sellerName));
            expLore.add(text("<gray>Buyer: <white>" + buyerName));
            expLore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
            expMeta.lore(expLore);
            expItem.setItemMeta(expMeta);
            inventory.setItem(22, expItem);
        }

        // -- Top 5 Sellers (slots 37-41) --
        List<String[]> topSellers = dao.getTopSellers(5);
        int[] topSlots = {37, 38, 39, 40, 41};

        for (int i = 0; i < topSellers.size() && i < topSlots.length; i++) {
            String[] data = topSellers.get(i);
            UUID sellerUuid = UUID.fromString(data[0]);
            int saleCount = Integer.parseInt(data[1]);
            double totalValue = Double.parseDouble(data[2]);
            String name = Bukkit.getOfflinePlayer(sellerUuid).getName();
            if (name == null) name = "Unknown";

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(sellerUuid));
            skullMeta.displayName(text("<gold>#" + (i + 1) + " <white>" + name));

            List<Component> skullLore = new ArrayList<>();
            skullLore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
            skullLore.add(text("<gray>Total Sales: <green>" + saleCount));
            skullLore.add(text("<gray>Total Value: <green>" + plugin.getEconomyManager().format(totalValue)));
            skullLore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
            skullMeta.lore(skullLore);
            skull.setItemMeta(skullMeta);
            inventory.setItem(topSlots[i], skull);
        }

        // -- Labels --
        ItemStack topLabel = new ItemStack(Material.DIAMOND);
        ItemMeta topLabelMeta = topLabel.getItemMeta();
        topLabelMeta.displayName(text("<aqua>Top Sellers"));
        topLabelMeta.lore(List.of(text("<gray>Top 5 sellers by sale count.")));
        topLabel.setItemMeta(topLabelMeta);
        inventory.setItem(31, topLabel);

        // -- Close Button (slot 49) --
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(text("<red>Close"));
        closeMeta.lore(List.of(text("<gray>Close the menu.")));
        close.setItemMeta(closeMeta);
        inventory.setItem(49, close);

        // Filler
        ItemStack filler = createThemedFiller();

        for (int i = 0; i < 54; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == 49) {
            viewer.closeInventory();
        }
    }
}
