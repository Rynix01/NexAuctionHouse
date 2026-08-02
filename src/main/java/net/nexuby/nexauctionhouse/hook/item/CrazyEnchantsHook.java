package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hook for CrazyEnchantments custom enchantment items.
 * Detects items that have custom enchantment data stored in PDC.
 */
public class CrazyEnchantsHook implements ItemHook {

    private static final NamespacedKey CE_KEY = new NamespacedKey("crazyenchantments", "enchantments");
    private final boolean available;

    public CrazyEnchantsHook() {
        this.available = Bukkit.getPluginManager().getPlugin("CrazyEnchantments") != null;
    }

    @Override
    public String getPluginName() {
        return "CrazyEnchantments";
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
        return null;
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        // These plugins modify existing item names with enchant info
        return null;
    }
}
