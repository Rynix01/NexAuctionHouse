package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionType;
import net.nexuby.nexauctionhouse.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class FavoritesGui extends PaginatedGui {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final List<Integer> auctionIds = new ArrayList<>();

    private int backSlot = -1;
    private int closeSlot = -1;

    public FavoritesGui(NexAuctionHouse plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String getGuiConfigName() {
        return "favorites";
    }

    @Override
    protected List<ItemStack> getDisplayItems() {
        auctionIds.clear();

        AuctionManager manager = plugin.getAuctionManager();
        List<AuctionItem> favorites = manager.getFavoriteAuctions(viewer.getUniqueId());

        FileConfiguration cfg = plugin.getGuiConfig().getGui(getGuiConfigName());
        List<String> loreTemplate = cfg != null ? cfg.getStringList("favorite-item-lore") : List.of();

        List<ItemStack> displayItems = new ArrayList<>();

        for (AuctionItem auction : favorites) {
            ItemStack display = auction.getItemStack().clone();
            ItemMeta meta = display.getItemMeta();

            List<Component> existingLore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();

            String currentBidStr = auction.getHighestBid() > 0
                    ? plugin.getEconomyManager().format(auction.getHighestBid(), auction.getCurrency())
                    : plugin.getLangManager().getRaw("bid.no-bids-yet");
            String bidderName = auction.getHighestBidderName() != null ? auction.getHighestBidderName() : "-";

            for (String line : loreTemplate) {
                String parsed = line
                        .replace("{seller}", auction.getSellerName())
                        .replace("{price}", plugin.getEconomyManager().format(auction.getPrice(), auction.getCurrency()))
                        .replace("{time}", TimeUtil.formatDuration(auction.getRemainingTime()))
                        .replace("{current_bid}", currentBidStr)
                        .replace("{bidder}", bidderName)
                        .replace("{type}", auction.isBidAuction() ? "Auction" : "BIN");
                existingLore.add(text(parsed));
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

        // Right-click opens item preview
        if (event.isRightClick() && !event.isShiftClick()) {
            new PreviewGui(plugin, viewer, auction.getItemStack(),
                    () -> new FavoritesGui(plugin, viewer).open()).open();
            return;
        }

        // Shift+click removes from favorites
        if (event.isShiftClick()) {
            plugin.getAuctionManager().removeFavorite(viewer, auctionId);
            viewer.sendMessage(plugin.getLangManager().prefixed("favorites.removed",
                    "{item}", AuctionManager.getItemName(auction.getItemStack())));
            refresh();
            return;
        }

        // Normal click opens purchase/bid dialog
        if (auction.isBidAuction()) {
            new BidGui(plugin, viewer, auction).open();
        } else {
            new ConfirmGui(plugin, viewer, auction).open();
        }
    }

    @Override
    protected void addExtraButtons(FileConfiguration cfg) {
        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons == null) return;

        if (buttons.contains("back")) {
            backSlot = buttons.getInt("back.slot", -1);
            if (backSlot >= 0) {
                inventory.setItem(backSlot, createButton(buttons.getConfigurationSection("back")));
            }
        }

        if (buttons.contains("close")) {
            closeSlot = buttons.getInt("close.slot", -1);
            if (closeSlot >= 0) {
                inventory.setItem(closeSlot, createButton(buttons.getConfigurationSection("close")));
            }
        }
    }

    @Override
    protected void handleExtraClick(InventoryClickEvent event, int slot) {
        if (slot == backSlot) {
            new MainMenu(plugin, viewer).open();
        } else if (slot == closeSlot) {
            viewer.closeInventory();
        }
    }
}
