package net.nexuby.nexauctionhouse.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Handles BungeeCord/Velocity plugin messaging for cross-server communication.
 * Registers the "BungeeCord" channel for proxy-level features like
 * server identification and player routing.
 */
public class BungeeCordListener implements PluginMessageListener {

    private static final String BUNGEECORD_CHANNEL = "BungeeCord";

    private final NexAuctionHouse plugin;
    private String currentServerName;

    public BungeeCordListener(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers the BungeeCord plugin messaging channel.
     */
    public void register() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEECORD_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BUNGEECORD_CHANNEL, this);
        plugin.getLogger().info("BungeeCord/Velocity plugin messaging channel registered.");

        // Request server name once a player joins
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentServerName == null && !Bukkit.getOnlinePlayers().isEmpty()) {
                requestServerName();
            }
        }, 100L, 200L);
    }

    /**
     * Unregisters the BungeeCord plugin messaging channel.
     */
    public void unregister() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEECORD_CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, BUNGEECORD_CHANNEL, this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!BUNGEECORD_CHANNEL.equals(channel)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(data);
        String subchannel = in.readUTF();

        if ("GetServer".equals(subchannel)) {
            this.currentServerName = in.readUTF();
            plugin.getLogger().info("BungeeCord server name detected: " + currentServerName);
        }
    }

    /**
     * Requests the current server name from the BungeeCord proxy.
     */
    private void requestServerName() {
        Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServer");
        player.sendPluginMessage(plugin, BUNGEECORD_CHANNEL, out.toByteArray());
    }

    /**
     * Returns the BungeeCord server name (may be null if not yet received).
     */
    public String getCurrentServerName() {
        return currentServerName;
    }
}
