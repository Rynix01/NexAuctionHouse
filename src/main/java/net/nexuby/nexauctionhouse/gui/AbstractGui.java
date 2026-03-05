package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for all custom GUIs. Implements InventoryHolder so we can
 * identify our menus in click events and prevent item duplication.
 */
public abstract class AbstractGui implements InventoryHolder {

    // Tracks which players currently have a GUI open - prevents concurrent menu exploits
    private static final Set<UUID> OPEN_GUIS = ConcurrentHashMap.newKeySet();

    protected final NexAuctionHouse plugin;
    protected final Player viewer;
    protected Inventory inventory;

    // Click cooldown per player to prevent rapid-fire exploits (millis)
    private static final long CLICK_COOLDOWN = 200L;
    private static final ConcurrentHashMap<UUID, Long> lastClickTime = new ConcurrentHashMap<>();

    public AbstractGui(NexAuctionHouse plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    /**
     * Build and populate the inventory contents.
     */
    protected abstract void build();

    /**
     * Handle a click event within this GUI.
     */
    public abstract void handleClick(InventoryClickEvent event);

    /**
     * Called when the GUI is closed.
     */
    public void onClose() {
        OPEN_GUIS.remove(viewer.getUniqueId());
    }

    /**
     * Opens the GUI for the viewer. Cancels if another GUI is being opened.
     */
    public void open() {
        if (!OPEN_GUIS.add(viewer.getUniqueId())) {
            // Player already has a GUI transition in progress, skip to avoid dupe
            return;
        }

        build();
        viewer.openInventory(inventory);
    }

    /**
     * Checks click cooldown to prevent rapid-fire item duplication.
     * Returns true if the click should be allowed.
     */
    public boolean checkCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastClickTime.get(player.getUniqueId());

        if (last != null && (now - last) < CLICK_COOLDOWN) {
            return false;
        }

        lastClickTime.put(player.getUniqueId(), now);
        return true;
    }

    /**
     * Marks the player as no longer having a GUI open.
     */
    public static void removeViewer(UUID uuid) {
        OPEN_GUIS.remove(uuid);
    }

    public static boolean hasOpenGui(UUID uuid) {
        return OPEN_GUIS.contains(uuid);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    // -- Helper methods for building GUI items from config --

    /**
     * Creates a simple ItemStack from a config section with material, name, and lore.
     */
    protected ItemStack createButton(ConfigurationSection section) {
        if (section == null) return new ItemStack(Material.STONE);

        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) material = Material.STONE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (section.contains("name")) {
            meta.displayName(MiniMessage.miniMessage().deserialize(section.getString("name")));
        }

        if (section.contains("lore")) {
            List<String> rawLore = section.getStringList("lore");
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : rawLore) {
                lore.add(MiniMessage.miniMessage().deserialize(line));
            }
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Fills specified slots with a decorative glass pane.
     */
    protected void applyFiller(FileConfiguration cfg) {
        if (cfg == null) return;

        ConfigurationSection filler = cfg.getConfigurationSection("filler");
        if (filler == null || !filler.getBoolean("enabled", false)) return;

        Material material = Material.matchMaterial(filler.getString("material", "BLACK_STAINED_GLASS_PANE"));
        if (material == null) material = Material.BLACK_STAINED_GLASS_PANE;

        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(filler.getString("name", " ")));
        pane.setItemMeta(meta);

        if (filler.contains("slots")) {
            for (int slot : filler.getIntegerList("slots")) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, pane);
                }
            }
        } else {
            // Fill all empty slots
            for (int i = 0; i < inventory.getSize(); i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, pane);
                }
            }
        }
    }

    protected Player getViewer() {
        return viewer;
    }
}
