package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hook for MythicMobs custom drop items.
 * Detects items via PersistentDataContainer key "mythicmobs:type".
 * Also supports MythicCrucible items via "mythiccrucible:id".
 */
public class MythicMobsHook implements ItemHook {

    private static final NamespacedKey MYTHIC_TYPE = new NamespacedKey("mythicmobs", "type");
    private static final NamespacedKey CRUCIBLE_ID = new NamespacedKey("mythiccrucible", "id");
    private final boolean available;

    public MythicMobsHook() {
        this.available = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
    }

    @Override
    public String getPluginName() {
        return "MythicMobs";
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

        // MythicMobs drop items
        if (pdc.has(MYTHIC_TYPE, PersistentDataType.STRING)) {
            return pdc.get(MYTHIC_TYPE, PersistentDataType.STRING);
        }

        // MythicCrucible custom items
        if (pdc.has(CRUCIBLE_ID, PersistentDataType.STRING)) {
            return "crucible:" + pdc.get(CRUCIBLE_ID, PersistentDataType.STRING);
        }

        return null;
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        // MythicMobs/Crucible items have display names set already
        return null;
    }
}
