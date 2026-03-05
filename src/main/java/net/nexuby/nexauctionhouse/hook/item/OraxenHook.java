package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hook for Oraxen custom items.
 * Detects items via PersistentDataContainer key "oraxen:id".
 */
public class OraxenHook implements ItemHook {

    private static final NamespacedKey ORAXEN_ID_KEY = new NamespacedKey("oraxen", "id");
    private final boolean available;

    public OraxenHook() {
        this.available = Bukkit.getPluginManager().getPlugin("Oraxen") != null;
    }

    @Override
    public String getPluginName() {
        return "Oraxen";
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

        if (pdc.has(ORAXEN_ID_KEY, PersistentDataType.STRING)) {
            return pdc.get(ORAXEN_ID_KEY, PersistentDataType.STRING);
        }
        return null;
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        // Oraxen sets display names on its items already
        return null;
    }
}
