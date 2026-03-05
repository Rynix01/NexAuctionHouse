package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.listener.ChatInputListener;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * A management GUI for a single auction item.
 * Allows the seller to edit price, extend duration, or cancel their auction.
 */
public class AuctionEditGui extends AbstractGui {

    private final AuctionItem auctionItem;

    // Slot positions in the 27-slot layout
    private static final int DISPLAY_SLOT = 4;
    private static final int EDIT_PRICE_SLOT = 10;
    private static final int EXTEND_1H_SLOT = 12;
    private static final int EXTEND_6H_SLOT = 13;
    private static final int EXTEND_12H_SLOT = 14;
    private static final int CANCEL_SLOT = 16;
    private static final int BACK_SLOT = 22;

    public AuctionEditGui(NexAuctionHouse plugin, Player viewer, AuctionItem auctionItem) {
        super(plugin, viewer);
        this.auctionItem = auctionItem;
    }

    @Override
    protected void build() {
        String itemName = AuctionManager.getItemName(auctionItem.getItemStack());
        inventory = Bukkit.createInventory(this, 27,
                text("<dark_gray>Manage: <white>" + itemName));

        // Display the auction item in center top
        ItemStack display = auctionItem.getItemStack().clone();
        ItemMeta displayMeta = display.getItemMeta();
        List<Component> displayLore = displayMeta.hasLore() ? new ArrayList<>(displayMeta.lore()) : new ArrayList<>();
        displayLore.add(Component.empty());
        displayLore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
        displayLore.add(text("<gray>Price: <green>" + plugin.getEconomyManager().format(auctionItem.getPrice(), auctionItem.getCurrency())));
        displayLore.add(text("<gray>Expires in: <yellow>" + TimeUtil.formatDuration(auctionItem.getRemainingTime())));
        displayLore.add(text("<gray>Tax rate: <red>" + String.format("%.1f%%", auctionItem.getTaxRate())));
        displayLore.add(text("<gray>You receive: <green>" + plugin.getEconomyManager().format(auctionItem.getSellerReceives(), auctionItem.getCurrency())));
        displayLore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
        displayMeta.lore(displayLore);
        display.setItemMeta(displayMeta);
        inventory.setItem(DISPLAY_SLOT, display);

        // Edit Price button
        ItemStack editPrice = new ItemStack(Material.NAME_TAG);
        ItemMeta editPriceMeta = editPrice.getItemMeta();
        editPriceMeta.displayName(text("<yellow>Edit Price"));
        editPriceMeta.lore(List.of(
                text("<gray>Current: <green>" + plugin.getEconomyManager().format(auctionItem.getPrice(), auctionItem.getCurrency())),
                Component.empty(),
                text("<gray>Click to type a new price"),
                text("<gray>in the chat.")
        ));
        editPrice.setItemMeta(editPriceMeta);
        inventory.setItem(EDIT_PRICE_SLOT, editPrice);

        // Extend duration buttons
        long maxDuration = plugin.getConfigManager().getMaxAuctionDuration() * 3600000L;
        long elapsed = System.currentTimeMillis() - auctionItem.getCreatedAt();
        long currentDuration = auctionItem.getExpiresAt() - auctionItem.getCreatedAt();
        boolean canExtend = currentDuration < maxDuration;

        inventory.setItem(EXTEND_1H_SLOT, createExtendButton(1, canExtend));
        inventory.setItem(EXTEND_6H_SLOT, createExtendButton(6, canExtend));
        inventory.setItem(EXTEND_12H_SLOT, createExtendButton(12, canExtend));

        // Cancel button
        ItemStack cancel = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(text("<red>Cancel Auction"));
        cancelMeta.lore(List.of(
                text("<gray>Remove this listing and"),
                text("<gray>get your item back."),
                Component.empty(),
                text("<red>This cannot be undone!")
        ));
        cancel.setItemMeta(cancelMeta);
        inventory.setItem(CANCEL_SLOT, cancel);

        // Back button
        ItemStack back = new ItemStack(Material.DARK_OAK_DOOR);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(text("<red>Back"));
        backMeta.lore(List.of(text("<gray>Return to your auctions.")));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);

        // Fill empty slots
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private ItemStack createExtendButton(int hours, boolean canExtend) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        if (canExtend) {
            meta.displayName(text("<green>Extend +" + hours + "h"));
            meta.lore(List.of(
                    text("<gray>Extend your auction by"),
                    text("<yellow>" + hours + " hour" + (hours > 1 ? "s" : "") + "<gray>."),
                    Component.empty(),
                    text("<gray>Max duration: <white>" + plugin.getConfigManager().getMaxAuctionDuration() + "h")
            ));
        } else {
            meta.displayName(text("<red>Extend +" + hours + "h"));
            meta.lore(List.of(
                    text("<red>Maximum duration reached!"),
                    text("<gray>Cannot extend further.")
            ));
        }

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!checkCooldown((Player) event.getWhoClicked())) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        // Verify the auction is still active
        AuctionItem current = plugin.getAuctionManager().getAuction(auctionItem.getId());
        if (current == null) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.auction-not-found"));
            viewer.closeInventory();
            return;
        }

        switch (slot) {
            case EDIT_PRICE_SLOT -> handleEditPrice();
            case EXTEND_1H_SLOT -> handleExtend(1);
            case EXTEND_6H_SLOT -> handleExtend(6);
            case EXTEND_12H_SLOT -> handleExtend(12);
            case CANCEL_SLOT -> handleCancel();
            case BACK_SLOT -> new MyAuctionsGui(plugin, viewer).open();
        }
    }

    private void handleEditPrice() {
        viewer.closeInventory();
        viewer.sendMessage(plugin.getLangManager().prefixed("auction.enter-new-price",
                "{min}", plugin.getEconomyManager().format(plugin.getConfigManager().getMinPrice(), auctionItem.getCurrency()),
                "{max}", plugin.getEconomyManager().format(plugin.getConfigManager().getMaxPrice(), auctionItem.getCurrency())));
        viewer.sendMessage(plugin.getLangManager().prefixed("auction.type-cancel"));

        ChatInputListener.awaitInput(viewer, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                viewer.sendMessage(plugin.getLangManager().prefixed("auction.price-edit-cancelled"));
                Bukkit.getScheduler().runTask(plugin, () -> new AuctionEditGui(plugin, viewer, auctionItem).open());
                return;
            }

            double newPrice;
            try {
                newPrice = Double.parseDouble(input);
            } catch (NumberFormatException e) {
                viewer.sendMessage(plugin.getLangManager().prefixed("auction.invalid-price"));
                Bukkit.getScheduler().runTask(plugin, () -> new AuctionEditGui(plugin, viewer, auctionItem).open());
                return;
            }

            if (newPrice < plugin.getConfigManager().getMinPrice()) {
                viewer.sendMessage(plugin.getLangManager().prefixed("auction.price-too-low",
                        "{min}", plugin.getEconomyManager().format(plugin.getConfigManager().getMinPrice(), auctionItem.getCurrency())));
                Bukkit.getScheduler().runTask(plugin, () -> new AuctionEditGui(plugin, viewer, auctionItem).open());
                return;
            }

            if (newPrice > plugin.getConfigManager().getMaxPrice()) {
                viewer.sendMessage(plugin.getLangManager().prefixed("auction.price-too-high",
                        "{max}", plugin.getEconomyManager().format(plugin.getConfigManager().getMaxPrice(), auctionItem.getCurrency())));
                Bukkit.getScheduler().runTask(plugin, () -> new AuctionEditGui(plugin, viewer, auctionItem).open());
                return;
            }

            boolean success = plugin.getAuctionManager().updatePrice(viewer, auctionItem.getId(), newPrice);
            if (success) {
                viewer.sendMessage(plugin.getLangManager().prefixed("auction.price-updated",
                        "{price}", plugin.getEconomyManager().format(newPrice, auctionItem.getCurrency())));
            } else {
                viewer.sendMessage(plugin.getLangManager().prefixed("auction.auction-not-found"));
            }

            Bukkit.getScheduler().runTask(plugin, () -> new AuctionEditGui(plugin, viewer, auctionItem).open());
        });
    }

    private void handleExtend(int hours) {
        boolean success = plugin.getAuctionManager().extendDuration(viewer, auctionItem.getId(), hours);

        if (success) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.duration-extended",
                    "{hours}", String.valueOf(hours)));
            // Refresh with updated data
            AuctionItem updated = plugin.getAuctionManager().getAuction(auctionItem.getId());
            if (updated != null) {
                new AuctionEditGui(plugin, viewer, updated).open();
            } else {
                new MyAuctionsGui(plugin, viewer).open();
            }
        } else {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.extend-failed"));
            // Refresh
            build();
            viewer.openInventory(inventory);
        }
    }

    private void handleCancel() {
        boolean cancelled = plugin.getAuctionManager().cancelAuction(viewer, auctionItem.getId(), false);

        if (cancelled) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.cancelled"));
        } else {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.auction-not-found"));
        }

        new MyAuctionsGui(plugin, viewer).open();
    }
}
