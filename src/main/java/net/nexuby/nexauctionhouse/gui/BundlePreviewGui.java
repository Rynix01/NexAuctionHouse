package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Displays all items inside one bundle using gui/bundle-preview.yml. */
public class BundlePreviewGui extends AbstractGui {

    private final AuctionItem auctionItem;
    private final Runnable backAction;
    private int backSlot = -1;

    public BundlePreviewGui(NexAuctionHouse plugin, Player viewer, AuctionItem auctionItem, Runnable backAction) {
        super(plugin, viewer);
        this.auctionItem = auctionItem;
        this.backAction = backAction;
    }

    @Override
    protected void build() {
        FileConfiguration gui = plugin.getGuiConfig().getGui("bundle-preview");
        if (gui == null) {
            plugin.getLogger().warning("GUI config 'bundle-preview' not found!");
            return;
        }

        inventory = Bukkit.createInventory(this, gui.getInt("size", 54),
                text(gui.getString("title", "<dark_gray>Bundle Preview")));

        List<Integer> itemSlots = gui.getIntegerList("item-slots");
        List<ItemStack> items = auctionItem.getBundleItems();
        for (int i = 0; i < items.size() && i < itemSlots.size(); i++) {
            int slot = itemSlots.get(i);
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, items.get(i).clone());
        }

        ConfigurationSection buttons = gui.getConfigurationSection("buttons");
        if (buttons != null) {
            placeButton(buttons.getConfigurationSection("info"),
                    "{seller}", escapeMiniMessage(auctionItem.getSellerName()),
                    "{count}", String.valueOf(items.size()),
                    "{price}", plugin.getEconomyManager().format(auctionItem.getPrice(), auctionItem.getCurrency()),
                    "{time}", TimeUtil.formatDuration(auctionItem.getRemainingTime()));
            backSlot = placeButton(buttons.getConfigurationSection("back"));
        }
        applyFiller(gui);
    }

    private int placeButton(ConfigurationSection section, String... replacements) {
        if (section == null) return -1;
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) return -1;
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) material = Material.STONE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(replace(section.getString("name", " "), replacements)));
        List<Component> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            lore.add(text(replace(line, replacements)));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
        return slot;
    }

    private static String replace(String value, String... replacements) {
        String result = value;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace(replacements[i], replacements[i + 1]);
        }
        return result;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize() || !checkCooldown(viewer)) return;
        if (slot == backSlot && backAction != null) backAction.run();
    }
}
