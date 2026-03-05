package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Hook for vanilla CustomModelData items.
 * Detects items that use the CustomModelData field for resource pack models.
 * Always available as it uses the native Paper API.
 */
@SuppressWarnings("deprecation")
public class CustomModelDataHook implements ItemHook {

    @Override
    public String getPluginName() {
        return "CustomModelData";
    }

    @Override
    public boolean isAvailable() {
        // Always available - built into Paper
        return true;
    }

    @Override
    public boolean isCustomItem(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().hasCustomModelData();
    }

    @Override
    public String getCustomItemId(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta.hasCustomModelData()) {
            return item.getType().name() + ":" + meta.getCustomModelData();
        }
        return null;
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        // CustomModelData items rely on resource packs for visuals,
        // we can't resolve names beyond what the display name already shows
        return null;
    }
}
