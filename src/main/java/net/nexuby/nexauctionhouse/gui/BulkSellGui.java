package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GUI that lets a player select items from their inventory for bulk listing.
 * Top 4 rows (36 slots) mirror the player's inventory.
 * Bottom row has confirm and cancel buttons.
 * Players click items to toggle selection, then confirm to list all selected items.
 */
public class BulkSellGui extends AbstractGui {

    private static final int GUI_SIZE = 54;
    private static final int PLAYER_SLOTS = 36;
    private static final int CONFIRM_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int CANCEL_SLOT = 50;

    private final double price;
    private final String currency;
    private final Set<Integer> selectedSlots = new HashSet<>();

    public BulkSellGui(NexAuctionHouse plugin, Player viewer, double price, String currency) {
        super(plugin, viewer);
        this.price = price;
        this.currency = currency;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, text("<dark_gray>Bulk Sell"));
    }

    @Override
    protected void build() {
        // Fill bottom row with glass
        ItemStack filler = createThemedFiller();
        for (int i = 36; i < GUI_SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Mirror player's inventory (slots 0-35) into GUI slots 0-35
        ItemStack[] playerContents = viewer.getInventory().getStorageContents();
        for (int i = 0; i < PLAYER_SLOTS && i < playerContents.length; i++) {
            ItemStack item = playerContents[i];
            if (item != null && item.getType() != Material.AIR) {
                if (selectedSlots.contains(i)) {
                    // Show selected state with enchant glow
                    ItemStack display = item.clone();
                    ItemMeta meta = display.getItemMeta();
                    List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                    lore.add(text(""));
                    lore.add(text("<green>✔ Selected for listing"));
                    lore.add(text("<gray>Price: <yellow>" + plugin.getEconomyManager().format(price, currency)));
                    lore.add(text("<yellow>Click to deselect."));
                    meta.lore(lore);
                    display.setItemMeta(meta);
                    inventory.setItem(i, display);
                } else {
                    ItemStack display = item.clone();
                    ItemMeta meta = display.getItemMeta();
                    List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                    lore.add(text(""));
                    lore.add(text("<gray>Click to select for listing."));
                    meta.lore(lore);
                    display.setItemMeta(meta);
                    inventory.setItem(i, display);
                }
            }
        }

        // Confirm button
        Material confirmMat = plugin.getThemeManager() != null
                ? plugin.getThemeManager().getButtonMaterial(viewer.getUniqueId(), "confirm") : null;
        ItemStack confirm = new ItemStack(confirmMat != null ? confirmMat : Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(text("<green>Confirm Bulk Sell"));
        confirmMeta.lore(List.of(
                text("<gray>Selected: <yellow>" + selectedSlots.size() + " item(s)"),
                text("<gray>Price each: <yellow>" + plugin.getEconomyManager().format(price, currency)),
                text(""),
                text("<yellow>Click to list all selected items.")
        ));
        confirm.setItemMeta(confirmMeta);
        inventory.setItem(CONFIRM_SLOT, confirm);

        // Info item
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(text("<gold>Bulk Sell Info"));
        infoMeta.lore(List.of(
                text("<gray>Click items above to select them."),
                text("<gray>Each selected item will be listed"),
                text("<gray>at the specified price."),
                text(""),
                text("<gray>Selected: <yellow>" + selectedSlots.size())
        ));
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO_SLOT, info);

        // Cancel button
        Material cancelMat = plugin.getThemeManager() != null
                ? plugin.getThemeManager().getButtonMaterial(viewer.getUniqueId(), "cancel") : null;
        ItemStack cancel = new ItemStack(cancelMat != null ? cancelMat : Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(text("<red>Cancel"));
        cancelMeta.lore(List.of(text("<gray>Return without listing.")));
        cancel.setItemMeta(cancelMeta);
        inventory.setItem(CANCEL_SLOT, cancel);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;

        if (!checkCooldown(viewer)) return;

        // Item selection area (player inventory mirror)
        if (slot < PLAYER_SLOTS) {
            ItemStack[] playerContents = viewer.getInventory().getStorageContents();
            if (slot < playerContents.length && playerContents[slot] != null
                    && playerContents[slot].getType() != Material.AIR) {
                if (selectedSlots.contains(slot)) {
                    selectedSlots.remove(slot);
                } else {
                    selectedSlots.add(slot);
                }
                // Rebuild to reflect selection changes
                inventory.clear();
                build();
            }
            return;
        }

        if (slot == CONFIRM_SLOT) {
            confirmBulkSell();
            return;
        }

        if (slot == CANCEL_SLOT) {
            viewer.closeInventory();
        }
    }

    private void confirmBulkSell() {
        if (selectedSlots.isEmpty()) {
            viewer.sendMessage(plugin.getLangManager().prefixed("bulk.no-items-selected"));
            return;
        }

        AuctionManager auctionManager = plugin.getAuctionManager();

        // Check listing limit
        int limit = auctionManager.getPlayerListingLimit(viewer);
        int currentListings = auctionManager.getPlayerActiveListings(viewer.getUniqueId());
        int available = limit - currentListings;

        if (available <= 0) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.listing-limit-reached",
                    "{limit}", String.valueOf(limit)));
            viewer.closeInventory();
            return;
        }

        int toList = Math.min(selectedSlots.size(), available);
        int listed = 0;
        int skipped = 0;

        List<Integer> sortedSlots = new ArrayList<>(selectedSlots);
        sortedSlots.sort(Integer::compareTo);

        for (int playerSlot : sortedSlots) {
            if (listed >= toList) {
                skipped += (selectedSlots.size() - listed - skipped);
                break;
            }

            ItemStack item = viewer.getInventory().getItem(playerSlot);
            if (item == null || item.getType() == Material.AIR) {
                skipped++;
                continue;
            }

            // Blacklist check
            if (!viewer.hasPermission("nexauctions.bypass.blacklist") && auctionManager.isBlacklisted(item)) {
                skipped++;
                continue;
            }

            // Per-material price limit check
            double[] materialLimits = auctionManager.getMaterialPriceLimits(item.getType().name());
            if (materialLimits != null && (price < materialLimits[0] || price > materialLimits[1])) {
                skipped++;
                continue;
            }

            ItemStack toSell = item.clone();
            viewer.getInventory().setItem(playerSlot, null);

            int auctionId = auctionManager.listItem(viewer, toSell, price, currency);
            if (auctionId > 0) {
                listed++;
            } else {
                // Failed to list, return item
                viewer.getInventory().setItem(playerSlot, toSell);
                skipped++;
            }
        }

        viewer.closeInventory();

        if (listed > 0) {
            viewer.sendMessage(plugin.getLangManager().prefixed("bulk.listed",
                    "{count}", String.valueOf(listed),
                    "{price}", plugin.getEconomyManager().format(price, currency)));
        }
        if (skipped > 0) {
            viewer.sendMessage(plugin.getLangManager().prefixed("bulk.skipped",
                    "{count}", String.valueOf(skipped)));
        }
        if (selectedSlots.size() > toList) {
            viewer.sendMessage(plugin.getLangManager().prefixed("bulk.limit-warning",
                    "{count}", String.valueOf(selectedSlots.size() - toList),
                    "{limit}", String.valueOf(limit)));
        }
    }
}
