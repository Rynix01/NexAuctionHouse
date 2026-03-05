package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hook for CrazyEnchantments / ExcellentEnchants custom enchantment items.
 * Detects items that have custom enchantment data stored in PDC.
 */
public class CrazyEnchantsHook implements ItemHook {

    private static final NamespacedKey CE_KEY = new NamespacedKey("crazyenchantments", "enchantments");
    private static final NamespacedKey EE_KEY = new NamespacedKey("excellentenchants", "enchant-id");
    private final boolean available;
    private final String activePlugin;

    public CrazyEnchantsHook() {
        if (Bukkit.getPluginManager().getPlugin("CrazyEnchantments") != null) {
            this.available = true;
            this.activePlugin = "CrazyEnchantments";
        } else if (Bukkit.getPluginManager().getPlugin("ExcellentEnchants") != null) {
            this.available = true;
            this.activePlugin = "ExcellentEnchants";
        } else {
            this.available = false;
            this.activePlugin = "CrazyEnchantments";
        }
    }

    @Override
    public String getPluginName() {
        return activePlugin;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean isCustomItem(ItemStack item) {
        return getCustomItemId(item) != null;
    }

    @Override
    public String getCustomItemId(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (pdc.has(CE_KEY, PersistentDataType.STRING)) {
            return "ce:" + pdc.get(CE_KEY, PersistentDataType.STRING);
        }
        if (pdc.has(EE_KEY, PersistentDataType.STRING)) {
            return "ee:" + pdc.get(EE_KEY, PersistentDataType.STRING);
        }
        return null;
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        // These plugins modify existing item names with enchant info
        return null;
    }
}
