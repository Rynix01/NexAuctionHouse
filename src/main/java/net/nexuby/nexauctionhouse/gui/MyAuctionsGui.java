package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
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

/**
 * Shows the player's own active auctions with an option to cancel them.
 * Uses its own fully configurable layout and only shows the player's listings.
 */
public class MyAuctionsGui extends PaginatedGui {

    private final List<Integer> auctionIds = new ArrayList<>();

    private int backSlot = -1;
    private int closeSlot = -1;
    private int expiredSlot = -1;

    public MyAuctionsGui(NexAuctionHouse plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String getGuiConfigName() {
        return "my-auctions";
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
        FileConfiguration cfg = plugin.getGuiConfig().getGui(getGuiConfigName());
        List<String> binLore = cfg != null ? cfg.getStringList("auction-item-lore") : List.of();
        List<String> bidLore = cfg != null ? cfg.getStringList("bid-item-lore") : List.of();

        for (AuctionItem auction : myAuctions) {
            ItemStack display = auction.getItemStack().clone();
            ItemMeta meta = display.getItemMeta();

            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            String currentBid = auction.getHighestBid() > 0
                    ? plugin.getEconomyManager().format(auction.getHighestBid(), auction.getCurrency())
                    : plugin.getLangManager().getRaw("bid.no-bids-yet");
            String bidder = auction.getHighestBidderName() != null ? auction.getHighestBidderName() : "-";
            List<String> template = auction.isBidAuction() && !bidLore.isEmpty() ? bidLore : binLore;
            for (String line : template) {
                lore.add(text(line
                        .replace("{price}", plugin.getEconomyManager().format(auction.getPrice(), auction.getCurrency()))
                        .replace("{current_bid}", currentBid)
                        .replace("{bidder}", escapeMiniMessage(bidder))
                        .replace("{time}", TimeUtil.formatDuration(auction.getRemainingTime()))
                        .replace("{tax}", String.format("%.1f%%", auction.getTaxRate()))));
            }

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

        if (buttons.contains("back")) {
            backSlot = buttons.getInt("back.slot", -1);
            if (backSlot >= 0) inventory.setItem(backSlot, createButton(buttons.getConfigurationSection("back")));
        }

        if (buttons.contains("expired-items")) {
            expiredSlot = buttons.getInt("expired-items.slot", -1);
            if (expiredSlot >= 0) inventory.setItem(expiredSlot, createButton(buttons.getConfigurationSection("expired-items")));
        }

        if (buttons.contains("close")) {
            closeSlot = buttons.getInt("close.slot", -1);
            if (closeSlot >= 0) inventory.setItem(closeSlot, createButton(buttons.getConfigurationSection("close")));
        }
    }

    @Override
    protected void handleExtraClick(InventoryClickEvent event, int slot) {
        if (slot == backSlot) {
            new MainMenu(plugin, viewer).open();
        } else if (slot == expiredSlot) {
            new ExpiredGui(plugin, viewer).open();
        } else if (slot == closeSlot) {
            viewer.closeInventory();
        }
    }
}
