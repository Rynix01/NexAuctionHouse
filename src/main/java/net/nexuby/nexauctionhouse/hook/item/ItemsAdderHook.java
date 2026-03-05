package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hook for ItemsAdder custom items.
 * Detects items via PersistentDataContainer key "itemsadder:ia_custom_id".
 * Falls back to ItemsAdder API if available for name resolution.
 */
public class ItemsAdderHook implements ItemHook {

    private static final NamespacedKey IA_ID_KEY = new NamespacedKey("itemsadder", "ia_custom_id");
    private final boolean available;

    public ItemsAdderHook() {
        this.available = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    @Override
    public String getPluginName() {
        return "ItemsAdder";
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

        if (pdc.has(IA_ID_KEY, PersistentDataType.STRING)) {
            return pdc.get(IA_ID_KEY, PersistentDataType.STRING);
        }
        return null;
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        // ItemsAdder items already have display names set
        // Try API for cleaner name resolution
        try {
            dev.lone.itemsadder.api.CustomStack stack = dev.lone.itemsadder.api.CustomStack.byItemStack(item);
            if (stack != null) {
                return stack.getDisplayName();
            }
        } catch (NoClassDefFoundError ignored) {
            // API not available, fall through
        }
        return null;
    }
}
