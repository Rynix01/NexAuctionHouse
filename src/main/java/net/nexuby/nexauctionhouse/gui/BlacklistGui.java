package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.config.ConfigManager;
import net.nexuby.nexauctionhouse.listener.ChatInputListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin GUI for viewing and managing blacklist configuration.
 * Shows blacklisted materials, enchantments, lore keywords, NBT tags,
 * disabled worlds, whitelist materials, and material price limits.
 */
public class BlacklistGui extends AbstractGui {

    private static final int MODE_SLOT = 4;
    private static final int MATERIALS_SLOT = 19;
    private static final int ENCHANTMENTS_SLOT = 20;
    private static final int LORE_SLOT = 21;
    private static final int NBT_SLOT = 22;
    private static final int CUSTOM_ITEMS_SLOT = 23;
    private static final int WORLDS_SLOT = 24;
    private static final int WHITELIST_SLOT = 25;
    private static final int PRICE_LIMITS_SLOT = 31;
    private static final int ADD_MATERIAL_SLOT = 37;
    private static final int ADD_ENCHANTMENT_SLOT = 38;
    private static final int ADD_LORE_SLOT = 39;
    private static final int ADD_NBT_SLOT = 40;
    private static final int ADD_WORLD_SLOT = 41;
    private static final int BACK_SLOT = 49;

    public BlacklistGui(NexAuctionHouse plugin, Player viewer) {
        super(plugin, viewer);
        this.inventory = Bukkit.createInventory(this, 54, text("<dark_red>Blacklist Manager"));
    }

    @Override
    protected void build() {
        ConfigManager config = plugin.getConfigManager();

        // Fill background
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // Mode indicator
        boolean isWhitelist = config.isWhitelistMode();
        ItemStack modeItem = new ItemStack(isWhitelist ? Material.WHITE_WOOL : Material.RED_WOOL);
        ItemMeta modeMeta = modeItem.getItemMeta();
        modeMeta.displayName(text("<gold>Blacklist Mode"));
        modeMeta.lore(List.of(
                text("<gray>Current mode: " + (isWhitelist ? "<green>Whitelist" : "<red>Blacklist")),
                text(""),
                isWhitelist
                        ? text("<gray>Only whitelisted materials can be listed.")
                        : text("<gray>Blacklisted items cannot be listed."),
                text(""),
                text("<yellow>Click to toggle mode.")
        ));
        modeItem.setItemMeta(modeMeta);
        inventory.setItem(MODE_SLOT, modeItem);

        // Materials
        List<String> materials = config.getBlacklistedMaterials();
        setInfoItem(MATERIALS_SLOT, Material.IRON_PICKAXE, "<red>Blacklisted Materials",
                materials, "<gray>Materials that cannot be listed.");

        // Enchantments
        List<String> enchantments = config.getBlacklistedEnchantments();
        setInfoItem(ENCHANTMENTS_SLOT, Material.ENCHANTED_BOOK, "<light_purple>Blacklisted Enchantments",
                enchantments, "<gray>Items with these enchantments are blocked.");

        // Lore keywords
        List<String> loreKeywords = config.getBlacklistedLoreKeywords();
        setInfoItem(LORE_SLOT, Material.WRITABLE_BOOK, "<yellow>Blacklisted Lore Keywords",
                loreKeywords, "<gray>Items with matching lore text are blocked.");

        // NBT tags
        List<String> nbtTags = config.getBlacklistedNbtTags();
        setInfoItem(NBT_SLOT, Material.NAME_TAG, "<aqua>Blacklisted NBT Tags",
                nbtTags, "<gray>Items with matching NBT data are blocked.");

        // Custom items
        List<String> customItems = config.getBlacklistedCustomItems();
        setInfoItem(CUSTOM_ITEMS_SLOT, Material.DIAMOND_SWORD, "<blue>Blacklisted Custom Items",
                customItems, "<gray>Plugin items blocked by ID.");

        // Disabled worlds
        List<String> worlds = config.getDisabledWorlds();
        setInfoItem(WORLDS_SLOT, Material.GRASS_BLOCK, "<green>Disabled Worlds",
                worlds, "<gray>AH cannot be used in these worlds.");

        // Whitelist materials
        List<String> whitelistMats = config.getWhitelistMaterials();
        setInfoItem(WHITELIST_SLOT, Material.WHITE_STAINED_GLASS, "<white>Whitelist Materials",
                whitelistMats, "<gray>Only these materials are allowed in whitelist mode.");

        // Material price limits
        Map<String, double[]> priceLimits = config.getMaterialPriceLimits();
        ItemStack priceItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta priceMeta = priceItem.getItemMeta();
        priceMeta.displayName(text("<gold>Material Price Limits"));
        List<Component> priceLore = new ArrayList<>();
        priceLore.add(text("<gray>Per-material min/max price overrides."));
        priceLore.add(text(""));
        if (priceLimits.isEmpty()) {
            priceLore.add(text("<dark_gray>No limits configured."));
        } else {
            int shown = 0;
            for (Map.Entry<String, double[]> entry : priceLimits.entrySet()) {
                if (shown >= 10) {
                    priceLore.add(text("<dark_gray>... and " + (priceLimits.size() - 10) + " more"));
                    break;
                }
                priceLore.add(text("<white>" + entry.getKey() + ": <yellow>"
                        + String.format("%.1f", entry.getValue()[0]) + " - " + String.format("%.1f", entry.getValue()[1])));
                shown++;
            }
        }
        priceLore.add(text(""));
        priceLore.add(text("<gray>Total: <yellow>" + priceLimits.size()));
        priceMeta.lore(priceLore);
        priceItem.setItemMeta(priceMeta);
        inventory.setItem(PRICE_LIMITS_SLOT, priceItem);

        // Add buttons
        setAddButton(ADD_MATERIAL_SLOT, Material.IRON_PICKAXE, "<green>+ Add Material",
                "<gray>Click to add a material to blacklist.");
        setAddButton(ADD_ENCHANTMENT_SLOT, Material.ENCHANTED_BOOK, "<green>+ Add Enchantment",
                "<gray>Click to add an enchantment to blacklist.");
        setAddButton(ADD_LORE_SLOT, Material.WRITABLE_BOOK, "<green>+ Add Lore Keyword",
                "<gray>Click to add a lore keyword to blacklist.");
        setAddButton(ADD_NBT_SLOT, Material.NAME_TAG, "<green>+ Add NBT Tag",
                "<gray>Click to add an NBT tag to blacklist.");
        setAddButton(ADD_WORLD_SLOT, Material.GRASS_BLOCK, "<green>+ Add Disabled World",
                "<gray>Click to add a world to disabled list.");

        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(text("<yellow>Back"));
        backMeta.lore(List.of(text("<gray>Return to admin menu.")));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);
    }

    private void setInfoItem(int slot, Material material, String name, List<String> entries, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name));

        List<Component> lore = new ArrayList<>();
        lore.add(text(description));
        lore.add(text(""));
        if (entries.isEmpty()) {
            lore.add(text("<dark_gray>None configured."));
        } else {
            int shown = 0;
            for (String entry : entries) {
                if (shown >= 10) {
                    lore.add(text("<dark_gray>... and " + (entries.size() - 10) + " more"));
                    break;
                }
                lore.add(text("<white>- " + entry));
                shown++;
            }
        }
        lore.add(text(""));
        lore.add(text("<gray>Total: <yellow>" + entries.size()));
        lore.add(text(""));
        lore.add(text("<red>Right-click to remove last entry."));

        meta.lore(lore);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private void setAddButton(int slot, Material material, String name, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name));
        meta.lore(List.of(text(description)));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;
        if (!checkCooldown(viewer)) return;

        switch (slot) {
            case MODE_SLOT -> toggleMode();
            case ADD_MATERIAL_SLOT -> promptAdd("blacklist.materials", "material name (e.g. DIAMOND_SWORD)");
            case ADD_ENCHANTMENT_SLOT -> promptAdd("blacklist.enchantments", "enchantment name (e.g. MENDING)");
            case ADD_LORE_SLOT -> promptAdd("blacklist.lore-keywords", "lore keyword (e.g. Soulbound)");
            case ADD_NBT_SLOT -> promptAdd("blacklist.nbt-tags", "NBT tag (e.g. myplugin:soulbound)");
            case ADD_WORLD_SLOT -> promptAdd("blacklist.disabled-worlds", "world name (e.g. world_pvp)");
            case MATERIALS_SLOT -> {
                if (event.isRightClick()) removeLast("blacklist.materials");
            }
            case ENCHANTMENTS_SLOT -> {
                if (event.isRightClick()) removeLast("blacklist.enchantments");
            }
            case LORE_SLOT -> {
                if (event.isRightClick()) removeLast("blacklist.lore-keywords");
            }
            case NBT_SLOT -> {
                if (event.isRightClick()) removeLast("blacklist.nbt-tags");
            }
            case CUSTOM_ITEMS_SLOT -> {
                if (event.isRightClick()) removeLast("blacklist.custom-items");
            }
            case WORLDS_SLOT -> {
                if (event.isRightClick()) removeLast("blacklist.disabled-worlds");
            }
            case WHITELIST_SLOT -> {
                if (event.isRightClick()) removeLast("blacklist.whitelist-materials");
            }
            case BACK_SLOT -> viewer.closeInventory();
        }
    }

    private void toggleMode() {
        boolean currentlyWhitelist = plugin.getConfigManager().isWhitelistMode();
        String newMode = currentlyWhitelist ? "blacklist" : "whitelist";

        plugin.getConfig().set("blacklist.mode", newMode);
        plugin.saveConfig();
        plugin.getConfigManager().load();

        viewer.sendMessage(plugin.getLangManager().prefixed("blacklist.mode-changed",
                "{mode}", newMode.toUpperCase()));

        inventory.clear();
        build();
    }

    private void promptAdd(String configPath, String hint) {
        viewer.closeInventory();
        viewer.sendMessage(plugin.getLangManager().prefixed("blacklist.enter-value",
                "{type}", hint));
        viewer.sendMessage(plugin.getLangManager().prefixed("auction.type-cancel"));

        ChatInputListener.awaitInput(viewer, input -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (input.equalsIgnoreCase("cancel")) {
                    viewer.sendMessage(plugin.getLangManager().prefixed("blacklist.action-cancelled"));
                    new BlacklistGui(plugin, viewer).open();
                    return;
                }

                String value = input.trim();
                List<String> list = new ArrayList<>(plugin.getConfig().getStringList(configPath));
                if (list.contains(value)) {
                    viewer.sendMessage(plugin.getLangManager().prefixed("blacklist.already-exists",
                            "{value}", value));
                } else {
                    list.add(value);
                    plugin.getConfig().set(configPath, list);
                    plugin.saveConfig();
                    plugin.getConfigManager().load();
                    viewer.sendMessage(plugin.getLangManager().prefixed("blacklist.added",
                            "{value}", value));
                }

                new BlacklistGui(plugin, viewer).open();
            });
        });
    }

    private void removeLast(String configPath) {
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(configPath));
        if (list.isEmpty()) {
            viewer.sendMessage(plugin.getLangManager().prefixed("blacklist.list-empty"));
            return;
        }

        String removed = list.removeLast();
        plugin.getConfig().set(configPath, list);
        plugin.saveConfig();
        plugin.getConfigManager().load();

        viewer.sendMessage(plugin.getLangManager().prefixed("blacklist.removed",
                "{value}", removed));

        inventory.clear();
        build();
    }
}
