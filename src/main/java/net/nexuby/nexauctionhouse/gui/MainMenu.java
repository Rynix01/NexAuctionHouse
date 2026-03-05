package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class MainMenu extends PaginatedGui {

    private final MiniMessage mm = MiniMessage.miniMessage();

    // Optional filter (for category browsing)
    private Predicate<AuctionItem> filter;
    private String categoryName;

    // Button slot positions loaded from config
    private int categoriesSlot = -1;
    private int myAuctionsSlot = -1;
    private int expiredSlot = -1;
    private int refreshSlot = -1;
    private int closeSlot = -1;

    // Maps item slot index -> auction id for click handling
    private final List<Integer> auctionIds = new ArrayList<>();

    public MainMenu(NexAuctionHouse plugin, Player viewer) {
        super(plugin, viewer);
    }

    public MainMenu(NexAuctionHouse plugin, Player viewer, Predicate<AuctionItem> filter, String categoryName) {
        super(plugin, viewer);
        this.filter = filter;
        this.categoryName = categoryName;
    }

    @Override
    protected String getGuiConfigName() {
        return "main-menu";
    }

    @Override
    protected List<ItemStack> getDisplayItems() {
        auctionIds.clear();

        AuctionManager manager = plugin.getAuctionManager();
        List<AuctionItem> auctions = new ArrayList<>(manager.getActiveAuctionsList());

        // Apply category filter if set
        if (filter != null) {
            auctions.removeIf(item -> !filter.test(item));
        }

        // Remove expired items from the display
        auctions.removeIf(AuctionItem::isExpired);

        // Sort by newest first
        auctions.sort(Comparator.comparingLong(AuctionItem::getCreatedAt).reversed());

        // Read lore template from config
        FileConfiguration cfg = plugin.getGuiConfig().getGui(getGuiConfigName());
        List<String> loreTemplate = cfg != null ? cfg.getStringList("auction-item-lore") : List.of();

        List<ItemStack> displayItems = new ArrayList<>();

        for (AuctionItem auction : auctions) {
            ItemStack display = auction.getItemStack().clone();
            ItemMeta meta = display.getItemMeta();

            // Build the auction info lore
            List<Component> existingLore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();

            for (String line : loreTemplate) {
                String parsed = line
                        .replace("{seller}", auction.getSellerName())
                        .replace("{price}", plugin.getEconomyManager().format(auction.getPrice()))
                        .replace("{time}", TimeUtil.formatDuration(auction.getRemainingTime()));
                existingLore.add(mm.deserialize(parsed));
            }

            meta.lore(existingLore);
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

        // Open confirmation menu
        new ConfirmGui(plugin, viewer, auction).open();
    }

    @Override
    protected void addExtraButtons(FileConfiguration cfg) {
        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons == null) return;

        // Categories button
        if (buttons.contains("categories")) {
            categoriesSlot = buttons.getInt("categories.slot", -1);
            if (categoriesSlot >= 0) {
                inventory.setItem(categoriesSlot, createButton(buttons.getConfigurationSection("categories")));
            }
        }

        // My Auctions button
        if (buttons.contains("my-auctions")) {
            myAuctionsSlot = buttons.getInt("my-auctions.slot", -1);
            if (myAuctionsSlot >= 0) {
                inventory.setItem(myAuctionsSlot, createButton(buttons.getConfigurationSection("my-auctions")));
            }
        }

        // Expired Items button
        if (buttons.contains("expired-items")) {
            expiredSlot = buttons.getInt("expired-items.slot", -1);
            if (expiredSlot >= 0) {
                inventory.setItem(expiredSlot, createButton(buttons.getConfigurationSection("expired-items")));
            }
        }

        // Refresh button
        if (buttons.contains("refresh")) {
            refreshSlot = buttons.getInt("refresh.slot", -1);
            if (refreshSlot >= 0) {
                inventory.setItem(refreshSlot, createButton(buttons.getConfigurationSection("refresh")));
            }
        }

        // Close button
        if (buttons.contains("close")) {
            closeSlot = buttons.getInt("close.slot", -1);
            if (closeSlot >= 0) {
                inventory.setItem(closeSlot, createButton(buttons.getConfigurationSection("close")));
            }
        }
    }

    @Override
    protected void handleExtraClick(InventoryClickEvent event, int slot) {
        if (slot == categoriesSlot) {
            new CategoryMenu(plugin, viewer).open();
        } else if (slot == myAuctionsSlot) {
            new MyAuctionsGui(plugin, viewer).open();
        } else if (slot == expiredSlot) {
            new ExpiredGui(plugin, viewer).open();
        } else if (slot == refreshSlot) {
            refresh();
        } else if (slot == closeSlot) {
            viewer.closeInventory();
        }
    }
}
