package net.nexuby.nexauctionhouse.manager;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.model.NotificationSettings;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationManager {

    private final NexAuctionHouse plugin;
    private final Map<UUID, NotificationSettings> cache = new ConcurrentHashMap<>();

    public NotificationManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the notification settings for a player, loading from database if not cached.
     * Returns default settings (from config) if no record exists.
     */
    public NotificationSettings getSettings(UUID playerUuid) {
        return cache.computeIfAbsent(playerUuid, uuid -> {
            AuctionDAO dao = plugin.getAuctionManager().getDao();
            NotificationSettings settings = dao.getNotificationSettings(uuid);
            if (settings == null) {
                settings = createDefaults(uuid);
            }
            return settings;
        });
    }

    /**
     * Creates default settings from config values.
     */
    private NotificationSettings createDefaults(UUID playerUuid) {
        var config = plugin.getConfigManager().getConfig();
        boolean sale = config.getBoolean("notifications.defaults.sale-notifications", true);
        boolean bid = config.getBoolean("notifications.defaults.bid-notifications", true);
        boolean sound = config.getBoolean("notifications.defaults.sound-effects", true);
        boolean login = config.getBoolean("notifications.defaults.login-notifications", true);
        boolean favorite = config.getBoolean("notifications.defaults.favorite-notifications", true);
        return NotificationSettings.defaults(playerUuid, sale, bid, sound, login, favorite);
    }

    /**
     * Saves settings to database and updates cache.
     */
    public void saveSettings(NotificationSettings settings) {
        cache.put(settings.getPlayerUuid(), settings);

        AuctionDAO dao = plugin.getAuctionManager().getDao();
        if (!plugin.getDatabaseManager().isUsingSQLite() && !plugin.getDatabaseManager().isUsingMongoDB()) {
            dao.saveNotificationSettingsMySQL(settings);
        } else {
            dao.saveNotificationSettings(settings);
        }
    }

    /**
     * Removes a player's settings from cache (e.g. on logout).
     */
    public void unloadSettings(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    // -- Notification check helpers --

    public boolean canReceiveSaleNotification(UUID playerUuid) {
        return getSettings(playerUuid).isSaleNotifications();
    }

    public boolean canReceiveBidNotification(UUID playerUuid) {
        return getSettings(playerUuid).isBidNotifications();
    }

    public boolean canReceiveLoginNotification(UUID playerUuid) {
        return getSettings(playerUuid).isLoginNotifications();
    }

    public boolean canReceiveFavoriteNotification(UUID playerUuid) {
        return getSettings(playerUuid).isFavoriteNotifications();
    }

    public boolean hasSoundEnabled(UUID playerUuid) {
        return getSettings(playerUuid).isSoundEffects();
    }

    // -- Sound effect helpers --

    public void playSaleSound(Player player) {
        if (!hasSoundEnabled(player.getUniqueId())) return;
        String soundName = plugin.getConfigManager().getConfig()
                .getString("notifications.sounds.sale", "ENTITY_EXPERIENCE_ORB_PICKUP");
        playSound(player, soundName);
    }

    public void playBidSound(Player player) {
        if (!hasSoundEnabled(player.getUniqueId())) return;
        String soundName = plugin.getConfigManager().getConfig()
                .getString("notifications.sounds.bid", "ENTITY_ARROW_HIT_PLAYER");
        playSound(player, soundName);
    }

    public void playFavoriteSound(Player player) {
        if (!hasSoundEnabled(player.getUniqueId())) return;
        String soundName = plugin.getConfigManager().getConfig()
                .getString("notifications.sounds.favorite", "BLOCK_NOTE_BLOCK_CHIME");
        playSound(player, soundName);
    }

    public void playRescuedSound(Player player) {
        if (!hasSoundEnabled(player.getUniqueId())) return;
        String soundName = plugin.getConfigManager().getConfig()
                .getString("notifications.sounds.rescued", "ENTITY_ITEM_PICKUP");
        playSound(player, soundName);
    }

    private void playSound(Player player, String soundName) {
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound name in config: " + soundName);
        }
    }
}
