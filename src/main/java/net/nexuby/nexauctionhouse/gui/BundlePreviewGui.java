package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
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
 * Preview GUI that shows all items contained in a bundle listing.
 * Items are laid out in a grid starting from slot 10 (skipping borders).
 */
public class BundlePreviewGui extends AbstractGui {

    private final AuctionItem auctionItem;
    private final Runnable backAction;

    private static final int BACK_SLOT = 45;
    private static final int INFO_SLOT = 4;

    // Inner slots (rows 1-4, columns 1-7) to display bundle items
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public BundlePreviewGui(NexAuctionHouse plugin, Player viewer, AuctionItem auctionItem, Runnable backAction) {
        super(plugin, viewer);
        this.auctionItem = auctionItem;
        this.backAction = backAction;
    }

    @Override
    protected void build() {
        inventory = Bukkit.createInventory(this, 54, text("<dark_gray>Bundle Preview"));

        // Fill background
        ItemStack filler = createThemedFiller();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Info item at top center
        ItemStack info = new ItemStack(Material.CHEST);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(text("<gold>Bundle Contents"));

        List<Component> infoLore = new ArrayList<>();
        infoLore.add(text("<gray>Seller: <yellow>" + auctionItem.getSellerName()));
        infoLore.add(text("<gray>Items: <yellow>" + auctionItem.getBundleItems().size()));
        infoLore.add(text("<gray>Price: <yellow>"
                + plugin.getEconomyManager().format(auctionItem.getPrice(), auctionItem.getCurrency())));
        infoLore.add(text("<gray>Time Left: <yellow>" + TimeUtil.formatDuration(auctionItem.getRemainingTime())));
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO_SLOT, info);

        // Display bundle items
        List<ItemStack> items = auctionItem.getBundleItems();
        for (int i = 0; i < items.size() && i < ITEM_SLOTS.length; i++) {
            inventory.setItem(ITEM_SLOTS[i], items.get(i).clone());
        }

        // Back button
        ItemStack back = new ItemStack(Material.DARK_OAK_DOOR);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(text("<yellow>Back"));
        backMeta.lore(List.of(text("<gray>Return to the previous menu.")));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (!checkCooldown(viewer)) return;

        if (slot == BACK_SLOT && backAction != null) {
            backAction.run();
        }
    }
}
