package net.nexuby.nexauctionhouse.listener;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Captures chat input from players who are currently in an input mode
 * (e.g. typing a new price for an auction).
 */
public class ChatInputListener implements Listener {

    private static final Map<UUID, Consumer<String>> pendingInputs = new ConcurrentHashMap<>();

    public ChatInputListener(NexAuctionHouse plugin) {
        // No-op, just needs the plugin reference for registration
    }

    /**
     * Registers a callback for the next chat message from a player.
     * The callback receives the raw message string.
     */
    public static void awaitInput(Player player, Consumer<String> callback) {
        pendingInputs.put(player.getUniqueId(), callback);
    }

    /**
     * Checks if a player is currently awaiting chat input.
     */
    public static boolean isAwaitingInput(UUID uuid) {
        return pendingInputs.containsKey(uuid);
    }

    /**
     * Cancels any pending input for a player.
     */
    public static void cancelInput(UUID uuid) {
        pendingInputs.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Consumer<String> callback = pendingInputs.remove(uuid);

        if (callback != null) {
            event.setCancelled(true);
            callback.accept(event.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
    }
}
