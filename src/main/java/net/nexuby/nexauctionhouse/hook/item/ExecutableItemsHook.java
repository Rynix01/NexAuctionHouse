package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hook for ExecutableItems custom items.
 * Detects items via PersistentDataContainer key "executableitems:ei-id".
 */
public class ExecutableItemsHook implements ItemHook {

    private static final NamespacedKey EI_ID = new NamespacedKey("executableitems", "ei-id");
    private final boolean available;

    public ExecutableItemsHook() {
        this.available = Bukkit.getPluginManager().getPlugin("ExecutableItems") != null;
    }

    @Override
    public String getPluginName() {
        return "ExecutableItems";
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

        if (pdc.has(EI_ID, PersistentDataType.STRING)) {
            return pdc.get(EI_ID, PersistentDataType.STRING);
        }
        return null;
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        return null;
    }
}
