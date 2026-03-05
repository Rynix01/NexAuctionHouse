package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hook for MMOItems custom items.
 * Detects items via PersistentDataContainer keys "mmoitems:type" and "mmoitems:id".
 */
public class MMOItemsHook implements ItemHook {

    private static final NamespacedKey MMOITEMS_TYPE = new NamespacedKey("mmoitems", "type");
    private static final NamespacedKey MMOITEMS_ID = new NamespacedKey("mmoitems", "id");
    private final boolean available;

    public MMOItemsHook() {
        this.available = Bukkit.getPluginManager().getPlugin("MMOItems") != null;
    }

    @Override
    public String getPluginName() {
        return "MMOItems";
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

        String type = pdc.get(MMOITEMS_TYPE, PersistentDataType.STRING);
        String id = pdc.get(MMOITEMS_ID, PersistentDataType.STRING);

        if (type != null && id != null) {
            return type + ":" + id;
        }
        return null;
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        // MMOItems sets display names on its items already
        return null;
    }
}
