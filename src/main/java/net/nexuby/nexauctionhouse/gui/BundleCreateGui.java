package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.config.ConfigManager;
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
 * GUI that lets a player select items from their inventory to create a bundle listing.
 * Top 4 rows (36 slots) mirror the player's inventory.
 * Bottom row has confirm, info, and cancel buttons.
 */
public class BundleCreateGui extends AbstractGui {

    private static final int GUI_SIZE = 54;
    private static final int PLAYER_SLOTS = 36;
    private static final int CONFIRM_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int CANCEL_SLOT = 50;

    private final double price;
    private final String currency;
    private final Set<Integer> selectedSlots = new HashSet<>();

    public BundleCreateGui(NexAuctionHouse plugin, Player viewer, double price, String currency) {
        super(plugin, viewer);
        this.price = price;
        this.currency = currency;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, text("<dark_gray>Create Bundle"));
    }

    @Override
    protected void build() {
        ConfigManager config = plugin.getConfigManager();
        int maxItems = config.getBundleMaxItems();

        // Fill bottom row with glass
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 36; i < GUI_SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Mirror player's inventory (slots 0-35) into GUI slots 0-35
        ItemStack[] playerContents = viewer.getInventory().getStorageContents();
        for (int i = 0; i < PLAYER_SLOTS && i < playerContents.length; i++) {
            ItemStack item = playerContents[i];
            if (item != null && item.getType() != Material.AIR) {
                ItemStack display = item.clone();
                ItemMeta meta = display.getItemMeta();
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(text(""));

                if (selectedSlots.contains(i)) {
                    lore.add(text("<green>✔ Added to bundle"));
                    lore.add(text("<yellow>Click to remove from bundle."));
                } else {
                    if (selectedSlots.size() < maxItems) {
                        lore.add(text("<gray>Click to add to bundle."));
                    } else {
                        lore.add(text("<red>Bundle is full! (" + maxItems + " items max)"));
                    }
                }

                meta.lore(lore);
                display.setItemMeta(meta);
                inventory.setItem(i, display);
            }
        }

        // Confirm button
        ItemStack confirm = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(text("<green>Create Bundle"));
        confirmMeta.lore(List.of(
                text("<gray>Items: <yellow>" + selectedSlots.size() + "/" + maxItems),
                text("<gray>Bundle Price: <yellow>" + plugin.getEconomyManager().format(price, currency)),
                text(""),
                text("<yellow>Click to list this bundle.")
        ));
        confirm.setItemMeta(confirmMeta);
        inventory.setItem(CONFIRM_SLOT, confirm);

        // Info item
        ItemStack info = new ItemStack(Material.CHEST);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(text("<gold>Bundle Info"));
        infoMeta.lore(List.of(
                text("<gray>Select items above to bundle them."),
                text("<gray>All selected items will be sold"),
                text("<gray>together as a single listing."),
                text(""),
                text("<gray>Selected: <yellow>" + selectedSlots.size() + "<gray>/<yellow>" + maxItems),
                text("<gray>Min items: <yellow>" + config.getBundleMinItems())
        ));
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO_SLOT, info);

        // Cancel button
        ItemStack cancel = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(text("<red>Cancel"));
        cancelMeta.lore(List.of(text("<gray>Return without creating bundle.")));
        cancel.setItemMeta(cancelMeta);
        inventory.setItem(CANCEL_SLOT, cancel);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;

        if (!checkCooldown(viewer)) return;

        // Item selection area
        if (slot < PLAYER_SLOTS) {
            ItemStack[] playerContents = viewer.getInventory().getStorageContents();
            if (slot < playerContents.length && playerContents[slot] != null
                    && playerContents[slot].getType() != Material.AIR) {
                if (selectedSlots.contains(slot)) {
                    selectedSlots.remove(slot);
                } else {
                    int maxItems = plugin.getConfigManager().getBundleMaxItems();
                    if (selectedSlots.size() >= maxItems) {
                        viewer.sendMessage(plugin.getLangManager().prefixed("bundle.max-items",
                                "{max}", String.valueOf(maxItems)));
                        return;
                    }
                    selectedSlots.add(slot);
                }
                inventory.clear();
                build();
            }
            return;
        }

        if (slot == CONFIRM_SLOT) {
            confirmBundle();
            return;
        }

        if (slot == CANCEL_SLOT) {
            viewer.closeInventory();
        }
    }

    private void confirmBundle() {
        ConfigManager config = plugin.getConfigManager();
        int minItems = config.getBundleMinItems();

        if (selectedSlots.size() < minItems) {
            viewer.sendMessage(plugin.getLangManager().prefixed("bundle.min-items",
                    "{min}", String.valueOf(minItems)));
            return;
        }

        AuctionManager auctionManager = plugin.getAuctionManager();

        // Check listing limit
        int limit = auctionManager.getPlayerListingLimit(viewer);
        int currentListings = auctionManager.getPlayerActiveListings(viewer.getUniqueId());
        if (currentListings >= limit) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.listing-limit-reached",
                    "{limit}", String.valueOf(limit)));
            viewer.closeInventory();
            return;
        }

        // Check bundle-specific limit
        int bundleLimit = config.getBundleLimit();
        if (bundleLimit > 0) {
            int currentBundles = auctionManager.getPlayerActiveBundles(viewer.getUniqueId());
            if (currentBundles >= bundleLimit) {
                viewer.sendMessage(plugin.getLangManager().prefixed("bundle.bundle-limit",
                        "{limit}", String.valueOf(bundleLimit)));
                viewer.closeInventory();
                return;
            }
        }

        // Collect items from player inventory
        List<ItemStack> bundleItems = new ArrayList<>();
        List<Integer> sortedSlots = new ArrayList<>(selectedSlots);
        sortedSlots.sort(Integer::compareTo);

        for (int playerSlot : sortedSlots) {
            ItemStack item = viewer.getInventory().getItem(playerSlot);
            if (item == null || item.getType() == Material.AIR) continue;

            // Blacklist check
            if (!viewer.hasPermission("nexauctions.bypass.blacklist") && auctionManager.isBlacklisted(item)) {
                viewer.sendMessage(plugin.getLangManager().prefixed("bundle.contains-blacklisted",
                        "{item}", AuctionManager.getItemName(item)));
                return;
            }

            bundleItems.add(item.clone());
        }

        if (bundleItems.size() < minItems) {
            viewer.sendMessage(plugin.getLangManager().prefixed("bundle.min-items",
                    "{min}", String.valueOf(minItems)));
            return;
        }

        // Remove items from player inventory
        for (int playerSlot : sortedSlots) {
            ItemStack item = viewer.getInventory().getItem(playerSlot);
            if (item != null && item.getType() != Material.AIR) {
                viewer.getInventory().setItem(playerSlot, null);
            }
        }

        int auctionId = auctionManager.listBundle(viewer, bundleItems, price, currency);

        viewer.closeInventory();

        if (auctionId > 0) {
            viewer.sendMessage(plugin.getLangManager().prefixed("bundle.listed",
                    "{count}", String.valueOf(bundleItems.size()),
                    "{price}", plugin.getEconomyManager().format(price, currency)));
        } else {
            // Failed - return items
            for (ItemStack bundleItem : bundleItems) {
                viewer.getInventory().addItem(bundleItem);
            }
            viewer.sendMessage(plugin.getLangManager().prefixed("bundle.create-failed"));
        }
    }
}
