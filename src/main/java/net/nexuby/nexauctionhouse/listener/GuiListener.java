package net.nexuby.nexauctionhouse.listener;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.gui.AbstractGui;
import net.nexuby.nexauctionhouse.manager.CursorProtectionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles all inventory interaction events for our custom GUIs.
 * This is the central anti-dupe protection layer:
 * - All clicks inside our GUIs are cancelled by default
 * - Drag events are fully blocked
 * - Cursor items are persisted to DB for crash protection
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

        // After click processing, track whatever is now on the cursor for crash protection
        Player player = (Player) event.getWhoClicked();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            CursorProtectionManager cpm = plugin.getCursorProtectionManager();
            ItemStack cursor = player.getItemOnCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                cpm.trackCursorItem(player.getUniqueId(), player.getName(), cursor);
            } else {
                cpm.clearTracked(player.getUniqueId());
            }
        }, 1L);
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

            Player player = (Player) event.getPlayer();
            CursorProtectionManager cpm = plugin.getCursorProtectionManager();

            // Return cursor item to inventory, or save to DB if full
            if (player.getItemOnCursor() != null && !player.getItemOnCursor().getType().isAir()) {
                ItemStack cursorItem = player.getItemOnCursor();
                var remaining = player.getInventory().addItem(cursorItem);
                if (!remaining.isEmpty()) {
                    // Inventory full - save to rescued items for later recovery
                    for (ItemStack leftover : remaining.values()) {
                        cpm.saveRescuedItem(player.getUniqueId(), player.getName(), leftover, "INVENTORY_FULL");
                    }
                }
                player.setItemOnCursor(null);
            }

            // Clear the crash-protection tracking since we handled the close normally
            cpm.clearTracked(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        CursorProtectionManager cpm = plugin.getCursorProtectionManager();

        // Clean up GUI tracking state
        AbstractGui.removeViewer(player.getUniqueId());

        // Handle cursor item on disconnect
        if (player.getItemOnCursor() != null && !player.getItemOnCursor().getType().isAir()) {
            ItemStack cursorItem = player.getItemOnCursor();
            var remaining = player.getInventory().addItem(cursorItem);
            if (!remaining.isEmpty()) {
                // Inventory full on disconnect - save to DB for recovery on next login
                for (ItemStack leftover : remaining.values()) {
                    cpm.saveRescuedItem(player.getUniqueId(), player.getName(), leftover, "DISCONNECT");
                }
            }
            player.setItemOnCursor(null);
        }

        // Clear tracking - the item is either returned or saved to rescued_items
        cpm.clearTracked(player.getUniqueId());
    }
}
