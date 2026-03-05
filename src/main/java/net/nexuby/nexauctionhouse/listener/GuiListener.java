package net.nexuby.nexauctionhouse.listener;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.gui.AbstractGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * Handles all inventory interaction events for our custom GUIs.
 * This is the central anti-dupe protection layer:
 * - All clicks inside our GUIs are cancelled by default
 * - Drag events are fully blocked
 * - Cursor items are handled on close/quit
 */
public class GuiListener implements Listener {

    private final NexAuctionHouse plugin;

    public GuiListener(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();

        if (!(topInventory.getHolder() instanceof AbstractGui gui)) {
            return;
        }

        // Block ALL clicks by default - the GUI handler decides what to do
        event.setCancelled(true);

        // Block shift-clicks from player inventory into our GUI
        if (event.getClickedInventory() != topInventory) {
            return;
        }

        // Delegate to the specific GUI handler
        gui.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();

        if (topInventory.getHolder() instanceof AbstractGui) {
            // Block all drag events in our GUIs - prevents item insertion exploits
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory topInventory = event.getView().getTopInventory();

        if (topInventory.getHolder() instanceof AbstractGui gui) {
            gui.onClose();

            // Safety: clear any item stuck on the cursor
            Player player = (Player) event.getPlayer();
            if (player.getItemOnCursor() != null && !player.getItemOnCursor().getType().isAir()) {
                // Return cursor item to inventory, or drop if full
                var remaining = player.getInventory().addItem(player.getItemOnCursor());
                if (!remaining.isEmpty()) {
                    remaining.values().forEach(item ->
                            player.getWorld().dropItemNaturally(player.getLocation(), item));
                }
                player.setItemOnCursor(null);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Clean up GUI tracking state
        AbstractGui.removeViewer(player.getUniqueId());

        // Handle cursor item on disconnect (prevents items vanishing)
        if (player.getItemOnCursor() != null && !player.getItemOnCursor().getType().isAir()) {
            var remaining = player.getInventory().addItem(player.getItemOnCursor());
            if (!remaining.isEmpty()) {
                remaining.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            player.setItemOnCursor(null);
        }
    }
}
