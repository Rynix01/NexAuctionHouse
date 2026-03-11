package net.nexuby.nexauctionhouse.redis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages cross-server auction house synchronization via Redis pub/sub.
 * Handles cache invalidation, distributed locking for expirations,
 * and cross-server notification delivery.
 */
public class CrossServerManager {

    private static final String CHANNEL_AUCTION = "auction";
    private static final String CHANNEL_NOTIFICATION = "notification";

    private final NexAuctionHouse plugin;
    private final RedisManager redisManager;
    private final String serverId;
    private final Gson gson = new Gson();

    public CrossServerManager(NexAuctionHouse plugin, RedisManager redisManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.serverId = plugin.getConfigManager().getServerId();
    }

    /**
     * Starts listening on Redis channels for cross-server sync events.
     */
    public void start() {
        redisManager.subscribe(CHANNEL_AUCTION, (channel, message) -> handleAuctionMessage(message));
        redisManager.subscribe(CHANNEL_NOTIFICATION, (channel, message) -> handleNotificationMessage(message));
        plugin.getLogger().info("Cross-server sync started. (Server ID: " + serverId + ")");
    }

    // ---- Publishing Methods (called by AuctionManager) ----

    /**
     * Publishes a new auction creation event so other servers can add it to their cache.
     */
    public void publishCreate(int auctionId) {
        JsonObject json = new JsonObject();
        json.addProperty("server", serverId);
        json.addProperty("type", "CREATE");
        json.addProperty("auctionId", auctionId);
        redisManager.publish(CHANNEL_AUCTION, gson.toJson(json));
    }

    /**
     * Publishes an auction removal event (sold/cancelled/expired).
     */
    public void publishRemove(int auctionId, String reason) {
        JsonObject json = new JsonObject();
        json.addProperty("server", serverId);
        json.addProperty("type", "REMOVE");
        json.addProperty("auctionId", auctionId);
        json.addProperty("reason", reason);
        redisManager.publish(CHANNEL_AUCTION, gson.toJson(json));
    }

    /**
     * Publishes an auction update event (bid placed, price changed, expiry extended).
     */
    public void publishUpdate(int auctionId) {
        JsonObject json = new JsonObject();
        json.addProperty("server", serverId);
        json.addProperty("type", "UPDATE");
        json.addProperty("auctionId", auctionId);
        redisManager.publish(CHANNEL_AUCTION, gson.toJson(json));
    }

    /**
     * Publishes a full cache refresh command to all servers.
     */
    public void publishRefresh() {
        JsonObject json = new JsonObject();
        json.addProperty("server", serverId);
        json.addProperty("type", "REFRESH");
        redisManager.publish(CHANNEL_AUCTION, gson.toJson(json));
    }

    /**
     * Publishes a cross-server notification to be delivered to a player on any server.
     */
    public void publishNotification(UUID playerUuid, String langKey, String... replacements) {
        JsonObject json = new JsonObject();
        json.addProperty("server", serverId);
        json.addProperty("playerUuid", playerUuid.toString());
        json.addProperty("langKey", langKey);
        json.addProperty("replacements", gson.toJson(replacements));
        json.addProperty("sound", "");
        redisManager.publish(CHANNEL_NOTIFICATION, gson.toJson(json));
    }

    /**
     * Publishes a cross-server notification with a sound effect.
     */
    public void publishNotificationWithSound(UUID playerUuid, String langKey, String sound, String... replacements) {
        JsonObject json = new JsonObject();
        json.addProperty("server", serverId);
        json.addProperty("playerUuid", playerUuid.toString());
        json.addProperty("langKey", langKey);
        json.addProperty("replacements", gson.toJson(replacements));
        json.addProperty("sound", sound);
        redisManager.publish(CHANNEL_NOTIFICATION, gson.toJson(json));
    }

    // ---- Distributed Locking ----

    /**
     * Attempts to acquire a distributed lock for processing an auction expiration.
     * Only one server should process each expiration.
     */
    public boolean tryLockExpiration(int auctionId) {
        return redisManager.tryLock("expire:" + auctionId, 30);
    }

    /**
     * Releases the expiration lock for an auction.
     */
    public void releaseExpirationLock(int auctionId) {
        redisManager.releaseLock("expire:" + auctionId);
    }

    // ---- Message Handlers (received from other servers) ----

    private void handleAuctionMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String sourceServer = json.get("server").getAsString();

            // Ignore messages from ourselves
            if (serverId.equals(sourceServer)) return;

            String type = json.get("type").getAsString();

            switch (type) {
                case "CREATE" -> handleRemoteCreate(json.get("auctionId").getAsInt());
                case "REMOVE" -> handleRemoteRemove(json.get("auctionId").getAsInt());
                case "UPDATE" -> handleRemoteUpdate(json.get("auctionId").getAsInt());
                case "REFRESH" -> handleRemoteRefresh();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to process cross-server auction message", e);
        }
    }

    private void handleNotificationMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String sourceServer = json.get("server").getAsString();

            // Ignore messages from ourselves (already handled locally)
            if (serverId.equals(sourceServer)) return;

            UUID playerUuid = UUID.fromString(json.get("playerUuid").getAsString());
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) return;

            String langKey = json.get("langKey").getAsString();
            String[] replacements = gson.fromJson(json.get("replacements").getAsString(), String[].class);
            String sound = json.get("sound").getAsString();

            // Deliver on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(plugin.getLangManager().prefixed(langKey, replacements));

                if (!sound.isEmpty() && plugin.getNotificationManager().hasSoundEnabled(playerUuid)) {
                    try {
                        org.bukkit.Sound bukSound = org.bukkit.Sound.valueOf(sound.toUpperCase());
                        player.playSound(player.getLocation(), bukSound, 1.0f, 1.0f);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to process cross-server notification message", e);
        }
    }

    /**
     * A new auction was created on another server — load it from DB and add to local cache.
     */
    private void handleRemoteCreate(int auctionId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                AuctionDAO dao = plugin.getAuctionManager().getDao();
                AuctionItem item = dao.getAuctionById(auctionId);
                if (item != null && !item.isExpired()) {
                    plugin.getAuctionManager().addToCache(item);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load remote auction #" + auctionId, e);
            }
        });
    }

    /**
     * An auction was sold/cancelled/expired on another server — remove from local cache.
     */
    private void handleRemoteRemove(int auctionId) {
        plugin.getAuctionManager().removeFromCache(auctionId);
    }

    /**
     * An auction was updated on another server — reload it from DB into local cache.
     */
    private void handleRemoteUpdate(int auctionId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                AuctionDAO dao = plugin.getAuctionManager().getDao();
                AuctionItem item = dao.getAuctionById(auctionId);
                if (item != null && !item.isExpired()) {
                    plugin.getAuctionManager().addToCache(item);
                } else {
                    plugin.getAuctionManager().removeFromCache(auctionId);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to reload remote auction #" + auctionId, e);
            }
        });
    }

    /**
     * A full cache refresh was requested — reload all active auctions from DB.
     */
    private void handleRemoteRefresh() {
        plugin.getLogger().info("Received cross-server cache refresh signal. Reloading auctions...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getAuctionManager().loadActiveAuctions();
        });
    }

    public String getServerId() {
        return serverId;
    }
}
