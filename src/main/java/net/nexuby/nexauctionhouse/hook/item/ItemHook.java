package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.inventory.ItemStack;

/**
 * Interface for third-party custom item plugin integrations.
 * Each implementation detects and provides information about items
 * from a specific plugin (ItemsAdder, Oraxen, MMOItems, etc.)
 */
public interface ItemHook {

    /**
     * Returns the name of the plugin this hook integrates with.
     */
    String getPluginName();

    /**
     * Returns true if the target plugin is installed and active.
     */
    boolean isAvailable();

    /**
     * Returns true if this ItemStack is a custom item from this plugin.
     */
    boolean isCustomItem(ItemStack item);

    /**
     * Returns the custom item ID as used by the plugin (e.g. "diamond_sword_epic").
     * Returns null if this item doesn't belong to this plugin.
     */
    String getCustomItemId(ItemStack item);

    /**
     * Returns the display name for this custom item.
     * Returns null to fall back to the default name resolver.
     */
    String getCustomItemName(ItemStack item);
}
