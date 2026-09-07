package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.config.ConfigManager;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/** GUI used to select player inventory items for a bundle listing. */
public class BundleCreateGui extends AbstractGui {

    private final double price;
    private final String currency;
    private final Set<Integer> selectedSlots = new HashSet<>();
    private List<Integer> playerSlots = List.of();
    private int confirmSlot = -1;
    private int cancelSlot = -1;

    public BundleCreateGui(NexAuctionHouse plugin, Player viewer, double price, String currency) {
        super(plugin, viewer);
        this.price = price;
        this.currency = currency;
    }

    @Override
    protected void build() {
        FileConfiguration gui = plugin.getGuiConfig().getGui("bundle-create");
        if (gui == null) {
            plugin.getLogger().warning("GUI config 'bundle-create' not found!");
            return;
        }

        int size = gui.getInt("size", 54);
        if (inventory == null || inventory.getSize() != size) {
            inventory = Bukkit.createInventory(this, size,
                    text(gui.getString("title", "<dark_gray>Create Bundle")));
        } else {
            inventory.clear();
        }

        playerSlots = new ArrayList<>(gui.getIntegerList("player-slots"));
        if (playerSlots.isEmpty()) {
            playerSlots = IntStream.range(0, Math.min(36, size)).boxed().toList();
        }

        ConfigManager config = plugin.getConfigManager();
        int maxItems = config.getBundleMaxItems();
        ItemStack[] playerContents = viewer.getInventory().getStorageContents();
        ConfigurationSection itemLore = gui.getConfigurationSection("item-lore");

        for (int playerSlot = 0; playerSlot < playerContents.length && playerSlot < playerSlots.size(); playerSlot++) {
            int guiSlot = playerSlots.get(playerSlot);
            if (guiSlot < 0 || guiSlot >= inventory.getSize()) continue;
            ItemStack original = playerContents[playerSlot];
            if (original == null || original.getType() == Material.AIR) continue;

            ItemStack display = original.clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            String loreKey = selectedSlots.contains(playerSlot)
                    ? "selected"
                    : selectedSlots.size() < maxItems ? "available" : "full";
            if (itemLore != null) {
                for (String line : itemLore.getStringList(loreKey)) {
                    lore.add(text(replace(line,
                            "{selected}", String.valueOf(selectedSlots.size()),
                            "{max}", String.valueOf(maxItems))));
                }
            }
            meta.lore(lore);
            display.setItemMeta(meta);
            inventory.setItem(guiSlot, display);
        }

        ConfigurationSection buttons = gui.getConfigurationSection("buttons");
        if (buttons != null) {
            confirmSlot = placeButton(buttons.getConfigurationSection("confirm"),
                    "{selected}", String.valueOf(selectedSlots.size()),
                    "{max}", String.valueOf(maxItems),
                    "{price}", plugin.getEconomyManager().format(price, currency),
                    "{min}", String.valueOf(config.getBundleMinItems()));
            placeButton(buttons.getConfigurationSection("info"),
                    "{selected}", String.valueOf(selectedSlots.size()),
                    "{max}", String.valueOf(maxItems),
                    "{price}", plugin.getEconomyManager().format(price, currency),
                    "{min}", String.valueOf(config.getBundleMinItems()));
            cancelSlot = placeButton(buttons.getConfigurationSection("cancel"));
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

        int playerSlot = playerSlots.indexOf(slot);
        if (playerSlot >= 0) {
            ItemStack[] contents = viewer.getInventory().getStorageContents();
            if (playerSlot < contents.length && contents[playerSlot] != null
                    && contents[playerSlot].getType() != Material.AIR) {
                if (!selectedSlots.remove(playerSlot)) {
                    int maxItems = plugin.getConfigManager().getBundleMaxItems();
                    if (selectedSlots.size() >= maxItems) {
                        viewer.sendMessage(plugin.getLangManager().prefixed("bundle.max-items",
                                "{max}", String.valueOf(maxItems)));
                        return;
                    }
                    selectedSlots.add(playerSlot);
                }
                build();
            }
            return;
        }

        if (slot == confirmSlot) confirmBundle();
        else if (slot == cancelSlot) viewer.closeInventory();
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
        int limit = auctionManager.getPlayerListingLimit(viewer);
        if (auctionManager.getPlayerActiveListings(viewer.getUniqueId()) >= limit) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.listing-limit-reached",
                    "{limit}", String.valueOf(limit)));
            viewer.closeInventory();
            return;
        }

        int bundleLimit = config.getBundleLimit();
        if (bundleLimit > 0 && auctionManager.getPlayerActiveBundles(viewer.getUniqueId()) >= bundleLimit) {
            viewer.sendMessage(plugin.getLangManager().prefixed("bundle.bundle-limit",
                    "{limit}", String.valueOf(bundleLimit)));
            viewer.closeInventory();
            return;
        }

        List<ItemStack> bundleItems = new ArrayList<>();
        List<Integer> sortedSlots = new ArrayList<>(selectedSlots);
        sortedSlots.sort(Integer::compareTo);
        for (int selected : sortedSlots) {
            ItemStack item = viewer.getInventory().getItem(selected);
            if (item == null || item.getType() == Material.AIR) continue;
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

        for (int selected : sortedSlots) {
            ItemStack item = viewer.getInventory().getItem(selected);
            if (item != null && item.getType() != Material.AIR) viewer.getInventory().setItem(selected, null);
        }

        int auctionId = auctionManager.listBundle(viewer, bundleItems, price, currency);
        viewer.closeInventory();
        if (auctionId > 0) {
            viewer.sendMessage(plugin.getLangManager().prefixed("bundle.listed",
                    "{count}", String.valueOf(bundleItems.size()),
                    "{price}", plugin.getEconomyManager().format(price, currency)));
        } else {
            for (ItemStack item : bundleItems) viewer.getInventory().addItem(item);
            viewer.sendMessage(plugin.getLangManager().prefixed("bundle.create-failed"));
        }
    }
}
