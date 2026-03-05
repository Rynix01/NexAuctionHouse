package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hook for HeadDatabase custom heads.
 * Detects items via PersistentDataContainer key "headdb:id".
 */
public class HeadDatabaseHook implements ItemHook {

    private static final NamespacedKey HDB_ID = new NamespacedKey("headdb", "id");
    private final boolean available;

    public HeadDatabaseHook() {
        this.available = Bukkit.getPluginManager().getPlugin("HeadDatabase") != null;
    }

    @Override
    public String getPluginName() {
        return "HeadDatabase";
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

        // HeadDatabase stores ID as integer
        if (pdc.has(HDB_ID, PersistentDataType.INTEGER)) {
            Integer id = pdc.get(HDB_ID, PersistentDataType.INTEGER);
            return id != null ? String.valueOf(id) : null;
        }
        // Some versions use string
        if (pdc.has(HDB_ID, PersistentDataType.STRING)) {
            return pdc.get(HDB_ID, PersistentDataType.STRING);
        }
        return null;
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        // HeadDatabase heads have display names set
        return null;
    }
}
