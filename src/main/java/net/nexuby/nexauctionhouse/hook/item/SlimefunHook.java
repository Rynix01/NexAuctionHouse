package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hook for Slimefun4 items.
 * Detects items via PersistentDataContainer key "slimefun:slimefun_item".
 */
public class SlimefunHook implements ItemHook {

    private static final NamespacedKey SF_ID = new NamespacedKey("slimefun", "slimefun_item");
    private final boolean available;

    public SlimefunHook() {
        this.available = Bukkit.getPluginManager().getPlugin("Slimefun") != null;
    }

    @Override
    public String getPluginName() {
        return "Slimefun";
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

        if (pdc.has(SF_ID, PersistentDataType.STRING)) {
            return pdc.get(SF_ID, PersistentDataType.STRING);
        }
        return null;
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        // Slimefun items have display names set already
        return null;
    }
}
