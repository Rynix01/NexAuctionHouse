package net.nexuby.nexauctionhouse.hook.item;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Central registry for all custom item plugin hooks.
 * Automatically detects which plugins are installed and registers their hooks.
 */
public class ItemHookManager {

    private final NexAuctionHouse plugin;
    private final List<ItemHook> hooks = new ArrayList<>();

    public ItemHookManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    /**
     * Detects installed plugins and registers their hooks.
     * Should be called during plugin startup.
     */
    public void registerAll() {
        registerHook(new ItemsAdderHook());
        registerHook(new OraxenHook());
        registerHook(new MMOItemsHook());
        registerHook(new MythicMobsHook());
        registerHook(new ExecutableItemsHook());
        registerHook(new EcoItemsHook());
        registerHook(new SlimefunHook());
        registerHook(new HeadDatabaseHook());
        registerHook(new CrazyEnchantsHook());
        registerHook(new NexoHook());
        registerHook(new CustomModelDataHook());

        long active = hooks.stream().filter(ItemHook::isAvailable).count();
        if (active > 0) {
            plugin.getLogger().info("Registered " + active + " item hook(s): " +
                    String.join(", ", hooks.stream()
                            .filter(ItemHook::isAvailable)
                            .map(ItemHook::getPluginName)
                            .toList()));
        }
    }

    private void registerHook(ItemHook hook) {
        try {
            if (hook.isAvailable()) {
                hooks.add(hook);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to register item hook for " + hook.getPluginName(), e);
        }
    }

    /**
     * Tries to resolve a display name from any available hook.
     * Returns null if no hook can identify the item.
     */
    public String getCustomItemName(ItemStack item) {
        for (ItemHook hook : hooks) {
            if (!hook.isAvailable()) continue;
            try {
                if (hook.isCustomItem(item)) {
                    String name = hook.getCustomItemName(item);
                    if (name != null) return name;
                }
            } catch (Exception ignored) {
                // Silently skip broken hooks
            }
        }
        return null;
    }

    /**
     * Returns the custom item id prefixed with its plugin name (e.g. "itemsadder:custom_sword").
     * Returns null if no hook recognizes this item.
     */
    public String getCustomItemId(ItemStack item) {
        for (ItemHook hook : hooks) {
            if (!hook.isAvailable()) continue;
            try {
                String id = hook.getCustomItemId(item);
                if (id != null) {
                    return hook.getPluginName().toLowerCase() + ":" + id;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Returns true if any hook recognizes this item as custom.
     */
    public boolean isCustomItem(ItemStack item) {
        for (ItemHook hook : hooks) {
            if (!hook.isAvailable()) continue;
            try {
                if (hook.isCustomItem(item)) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * Returns an unmodifiable list of all registered hooks.
     */
    public List<ItemHook> getHooks() {
        return Collections.unmodifiableList(hooks);
    }
}
