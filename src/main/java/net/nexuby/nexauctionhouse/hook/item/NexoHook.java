package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hook for Nexo (formerly Oraxen v2 rebrand) custom items.
 * Detects items via PersistentDataContainer key "nexo:id".
 */
public class NexoHook implements ItemHook {

    private static final NamespacedKey NEXO_ID = new NamespacedKey("nexo", "id");
    private final boolean available;

    public NexoHook() {
        this.available = Bukkit.getPluginManager().getPlugin("Nexo") != null;
    }

    @Override
    public String getPluginName() {
        return "Nexo";
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

        if (pdc.has(NEXO_ID, PersistentDataType.STRING)) {
            return pdc.get(NEXO_ID, PersistentDataType.STRING);
        }
        return null;
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        return null;
    }
}
