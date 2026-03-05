package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shows the player's own active auctions with an option to cancel them.
 * Uses the main-menu GUI layout but only shows the player's listings.
 */
public class MyAuctionsGui extends PaginatedGui {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final List<Integer> auctionIds = new ArrayList<>();

    private int backSlot = -1;
    private int closeSlot = -1;

    public MyAuctionsGui(NexAuctionHouse plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String getGuiConfigName() {
        // Reuses the main-menu layout
        return "main-menu";
    }

    @Override
    protected void build() {
        FileConfiguration cfg = plugin.getGuiConfig().getGui(getGuiConfigName());
        if (cfg == null) return;

        int size = cfg.getInt("size", 54);
        inventory = Bukkit.createInventory(this, size,
                text("<dark_gray>My Auctions"));

        itemSlots = cfg.getIntegerList("item-slots");

        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons != null) {
            if (buttons.contains("previous-page.slot")) {
                prevPageSlot = buttons.getInt("previous-page.slot");
            }
            if (buttons.contains("next-page.slot")) {
                nextPageSlot = buttons.getInt("next-page.slot");
            }
        }

        pageItems = getDisplayItems();

        // Manually populate since we override build()
        int startIndex = currentPage * itemSlots.size();
        int endIndex = Math.min(startIndex + itemSlots.size(), pageItems.size());

        for (int i = 0; i < itemSlots.size(); i++) {
            int dataIndex = startIndex + i;
            if (dataIndex < endIndex) {
                inventory.setItem(itemSlots.get(i), pageItems.get(dataIndex));
            }
        }

        if (buttons != null) {
            if (prevPageSlot >= 0 && currentPage > 0) {
                inventory.setItem(prevPageSlot, createButton(buttons.getConfigurationSection("previous-page")));
            }
            if (nextPageSlot >= 0 && endIndex < pageItems.size()) {
                inventory.setItem(nextPageSlot, createButton(buttons.getConfigurationSection("next-page")));
            }
        }

        addExtraButtons(cfg);
        applyFiller(cfg);
    }

    @Override
    protected List<ItemStack> getDisplayItems() {
        auctionIds.clear();

        AuctionManager manager = plugin.getAuctionManager();
        List<AuctionItem> myAuctions = new ArrayList<>();

        for (AuctionItem item : manager.getActiveAuctionsList()) {
            if (item.getSellerUuid().equals(viewer.getUniqueId()) && !item.isExpired()) {
                myAuctions.add(item);
            }
        }

        myAuctions.sort(Comparator.comparingLong(AuctionItem::getCreatedAt).reversed());

        List<ItemStack> displayItems = new ArrayList<>();

        for (AuctionItem auction : myAuctions) {
            ItemStack display = auction.getItemStack().clone();
            ItemMeta meta = display.getItemMeta();

            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
            lore.add(text("<gray>Price: <green>" + plugin.getEconomyManager().format(auction.getPrice(), auction.getCurrency())));
            lore.add(text("<gray>Expires in: <yellow>" + TimeUtil.formatDuration(auction.getRemainingTime())));
            lore.add(text("<gray>Tax rate: <red>" + String.format("%.1f%%", auction.getTaxRate())));
            lore.add(Component.empty());
            lore.add(text("<yellow>Click to manage this auction!"));
            lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));

            meta.lore(lore);
            display.setItemMeta(meta);
            displayItems.add(display);
            auctionIds.add(auction.getId());
        }

        return displayItems;
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, int itemIndex) {
        if (itemIndex >= auctionIds.size()) return;

        int auctionId = auctionIds.get(itemIndex);
        AuctionItem auction = plugin.getAuctionManager().getAuction(auctionId);

        if (auction == null) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.auction-not-found"));
            refresh();
            return;
        }

        new AuctionEditGui(plugin, viewer, auction).open();
    }

    @Override
    protected void addExtraButtons(FileConfiguration cfg) {
        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons == null) return;

        // Back button (reuse close slot as back)
        if (buttons.contains("close")) {
            closeSlot = buttons.getInt("close.slot", -1);
            if (closeSlot >= 0) {
                // Override close with back functionality
                org.bukkit.Material mat = org.bukkit.Material.DARK_OAK_DOOR;
                ItemStack back = new ItemStack(mat);
                ItemMeta meta = back.getItemMeta();
                meta.displayName(text("<red>Back"));
                meta.lore(List.of(text("<gray>Return to the main menu.")));
                back.setItemMeta(meta);
                inventory.setItem(closeSlot, back);
                backSlot = closeSlot;
            }
        }
    }

    @Override
    protected void handleExtraClick(InventoryClickEvent event, int slot) {
        if (slot == backSlot) {
            new MainMenu(plugin, viewer).open();
        }
    }
}
