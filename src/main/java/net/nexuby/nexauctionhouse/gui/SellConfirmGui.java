package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirmation dialog before listing an item for sale.
 * Shows the item, price, duration, and tax info.
 * On confirm, calls the listing logic and gives feedback.
 */
public class SellConfirmGui extends AbstractGui {

    private final ItemStack itemToSell;
    private final double price;
    private final String currency;
    private final boolean isBidAuction;
    private final boolean isAutoRelist;

    private int confirmSlot = -1;
    private int cancelSlot = -1;
    private int displaySlot = -1;
    private boolean confirmed;

    public SellConfirmGui(NexAuctionHouse plugin, Player viewer, ItemStack itemToSell,
                          double price, String currency, boolean isBidAuction, boolean isAutoRelist) {
        super(plugin, viewer);
        this.itemToSell = itemToSell;
        this.price = price;
        this.currency = currency;
        this.isBidAuction = isBidAuction;
        this.isAutoRelist = isAutoRelist;
    }

    @Override
    protected void build() {
        FileConfiguration cfg = plugin.getGuiConfig().getGui("sell-confirm");
        if (cfg == null) {
            plugin.getLogger().warning("GUI config 'sell-confirm' not found!");
            return;
        }

        String title = cfg.getString("title", "<dark_gray>Confirm Listing");
        int size = cfg.getInt("size", 27);
        inventory = Bukkit.createInventory(this, size, text(title));

        AuctionManager manager = plugin.getAuctionManager();
        int durationHours = manager.getPlayerAuctionDuration(viewer);
        if (isBidAuction) {
            durationHours = plugin.getConfigManager().getBidDefaultDuration();
        }
        double taxRate = manager.getPlayerTaxRate(viewer);

        String priceStr = plugin.getEconomyManager().format(price, currency);
        String durationStr = TimeUtil.formatDuration(durationHours * 3600L);
        String taxStr = String.format("%.1f", taxRate);

        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons != null) {
            if (buttons.contains("confirm")) {
                confirmSlot = buttons.getInt("confirm.slot", 11);
                ItemStack confirmItem = createButton(buttons.getConfigurationSection("confirm"));
                if (confirmItem.hasItemMeta()) {
                    ItemMeta meta = confirmItem.getItemMeta();
                    ConfigurationSection confirmCfg = buttons.getConfigurationSection("confirm");
                    if (confirmCfg != null && confirmCfg.contains("lore")) {
                        List<Component> lore = new ArrayList<>();
                        for (String line : confirmCfg.getStringList("lore")) {
                            lore.add(text(line
                                    .replace("{price}", priceStr)
                                    .replace("{duration}", durationStr)
                                    .replace("{tax}", taxStr)));
                        }
                        meta.lore(lore);
                        confirmItem.setItemMeta(meta);
                    }
                }
                inventory.setItem(confirmSlot, confirmItem);
            }

            if (buttons.contains("cancel")) {
                cancelSlot = buttons.getInt("cancel.slot", 15);
                inventory.setItem(cancelSlot, createButton(buttons.getConfigurationSection("cancel")));
            }

            if (buttons.contains("display-item")) {
                displaySlot = buttons.getInt("display-item.slot", 13);
                inventory.setItem(displaySlot, itemToSell.clone());
            }
        }

        applyFiller(cfg);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!checkCooldown((Player) event.getWhoClicked())) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        if (slot == confirmSlot) {
            confirmListing();
        } else if (slot == cancelSlot) {
            cancelListing();
        }
    }

    private void confirmListing() {
        confirmed = true;
        viewer.closeInventory();

        AuctionManager auctionManager = plugin.getAuctionManager();

        int auctionId;
        if (isBidAuction) {
            auctionId = auctionManager.listBidItem(viewer, itemToSell, price, currency, isAutoRelist);
        } else {
            auctionId = auctionManager.listItem(viewer, itemToSell, price, currency, isAutoRelist);
        }

        if (auctionId > 0) {
            if (isBidAuction) {
                viewer.sendMessage(plugin.getLangManager().prefixed("bid.listed",
                        "{price}", plugin.getEconomyManager().format(price, currency)));
            } else {
                viewer.sendMessage(plugin.getLangManager().prefixed("auction.listed",
                        "{price}", plugin.getEconomyManager().format(price, currency)));
            }
            if (isAutoRelist) {
                viewer.sendMessage(plugin.getLangManager().prefixed("auto-relist.enabled",
                        "{max}", String.valueOf(plugin.getConfigManager().getMaxAutoRelists())));
            }
        } else {
            // Failed - give item back
            viewer.getInventory().setItemInMainHand(itemToSell);
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.auction-not-found"));
        }
    }

    private void cancelListing() {
        confirmed = true;
        viewer.closeInventory();
        viewer.getInventory().setItemInMainHand(itemToSell);
        viewer.sendMessage(plugin.getLangManager().prefixed("auction.listing-cancelled"));
    }

    @Override
    public void onClose() {
        super.onClose();
        if (!confirmed) {
            viewer.getInventory().setItemInMainHand(itemToSell);
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.listing-cancelled"));
        }
    }
}
