package net.nexuby.nexauctionhouse.config;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final NexAuctionHouse plugin;
    private FileConfiguration config;

    public ConfigManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // -- Database settings --

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getMySQLHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMySQLPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMySQLDatabase() {
        return config.getString("database.mysql.database", "nexauctionhouse");
    }

    public String getMySQLUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getMySQLPassword() {
        return config.getString("database.mysql.password", "");
    }

    public boolean getMySQLSSL() {
        return config.getBoolean("database.mysql.use-ssl", false);
    }

    // -- MongoDB settings --

    public String getMongoConnectionString() {
        return config.getString("database.mongodb.connection-string", "mongodb://localhost:27017");
    }

    public String getMongoDatabase() {
        return config.getString("database.mongodb.database", "nexauctionhouse");
    }

    // -- General settings --

    public String getDefaultLanguage() {
        return config.getString("general.language", "en");
    }

    public int getDefaultAuctionDuration() {
        return config.getInt("general.default-auction-duration", 48);
    }

    public int getMaxAuctionDuration() {
        return config.getInt("general.max-auction-duration", 72);
    }

    public double getMinPrice() {
        return config.getDouble("general.min-price", 1.0);
    }

    public double getMaxPrice() {
        return config.getDouble("general.max-price", 1000000.0);
    }

    public int getDefaultListingLimit() {
        return config.getInt("general.default-listing-limit", 5);
    }

    public int getExpiredItemKeepDays() {
        return config.getInt("general.expired-item-keep-days", 7);
    }

    // -- Tax settings --

    public double getDefaultTaxRate() {
        return config.getDouble("tax.default-rate", 10.0);
    }

    public boolean isTaxEnabled() {
        return config.getBoolean("tax.enabled", true);
    }

    // -- Bid settings --

    public boolean isBidEnabled() {
        return config.getBoolean("bid.enabled", true);
    }

    public double getBidMinIncrementPercent() {
        return config.getDouble("bid.min-increment-percent", 5.0);
    }

    public int getAntiSnipeSeconds() {
        return config.getInt("bid.anti-snipe-seconds", 300);
    }

    public int getBidDefaultDuration() {
        return config.getInt("bid.default-duration", 24);
    }

    // -- Favorites settings --

    public int getMaxFavorites() {
        return config.getInt("favorites.max-favorites", 50);
    }

    // -- Statistics settings --

    public int getStatsCacheDurationMinutes() {
        return config.getInt("stats.cache-duration-minutes", 10);
    }

    public int getHistoryLimit() {
        return config.getInt("stats.history-limit", 50);
    }

    // -- Blacklist --

    public String getBlacklistMode() {
        return config.getString("blacklist.mode", "blacklist");
    }

    public boolean isWhitelistMode() {
        return "whitelist".equalsIgnoreCase(getBlacklistMode());
    }

    public List<String> getBlacklistedMaterials() {
        return config.getStringList("blacklist.materials");
    }

    public List<String> getBlacklistedLoreKeywords() {
        return config.getStringList("blacklist.lore-keywords");
    }

    public List<String> getBlacklistedCustomItems() {
        return config.getStringList("blacklist.custom-items");
    }

    public List<String> getBlacklistedEnchantments() {
        return config.getStringList("blacklist.enchantments");
    }

    public List<String> getBlacklistedNbtTags() {
        return config.getStringList("blacklist.nbt-tags");
    }

    public List<String> getDisabledWorlds() {
        return config.getStringList("blacklist.disabled-worlds");
    }

    public List<String> getWhitelistMaterials() {
        return config.getStringList("blacklist.whitelist-materials");
    }

    /**
     * Returns per-material price limits as a map of material name -> [min, max].
     */
    public Map<String, double[]> getMaterialPriceLimits() {
        ConfigurationSection section = config.getConfigurationSection("blacklist.material-price-limits");
        if (section == null) return Collections.emptyMap();

        Map<String, double[]> limits = new HashMap<>();
        for (String material : section.getKeys(false)) {
            double min = section.getDouble(material + ".min", getMinPrice());
            double max = section.getDouble(material + ".max", getMaxPrice());
            limits.put(material.toUpperCase(), new double[]{min, max});
        }
        return limits;
    }

    // -- Discord Webhook --

    public boolean isDiscordEnabled() {
        return config.getBoolean("discord.enabled", false);
    }

    public String getDiscordWebhookUrl() {
        return config.getString("discord.webhook-url", "");
    }

    // -- Auto-Relist settings --

    public boolean isAutoRelistEnabled() {
        return config.getBoolean("auto-relist.enabled", true);
    }

    public int getMaxAutoRelists() {
        return config.getInt("auto-relist.max-relists", 3);
    }

    public double getAutoRelistCostPercent() {
        return config.getDouble("auto-relist.cost-percent", 0.0);
    }

    // -- Bundle settings --

    public boolean isBundleEnabled() {
        return config.getBoolean("bundles.enabled", true);
    }

    public int getBundleMaxItems() {
        return config.getInt("bundles.max-items", 9);
    }

    public int getBundleMinItems() {
        return config.getInt("bundles.min-items", 2);
    }

    public int getBundleLimit() {
        return config.getInt("bundles.bundle-limit", 0);
    }

    // -- Cross-server settings --

    public boolean isCrossServerEnabled() {
        return config.getBoolean("cross-server.enabled", false);
    }

    public String getServerId() {
        return config.getString("cross-server.server-id", "server-1");
    }

    public String getRedisHost() {
        return config.getString("cross-server.redis.host", "localhost");
    }

    public int getRedisPort() {
        return config.getInt("cross-server.redis.port", 6379);
    }

    public String getRedisPassword() {
        return config.getString("cross-server.redis.password", "");
    }

    public int getRedisDatabase() {
        return config.getInt("cross-server.redis.database", 0);
    }

    public int getRedisTimeout() {
        return config.getInt("cross-server.redis.timeout", 3000);
    }

    public int getRedisMaxPoolSize() {
        return config.getInt("cross-server.redis.max-pool-size", 10);
    }

    public String getRedisChannelPrefix() {
        return config.getString("cross-server.redis.channel-prefix", "nexah");
    }

    public boolean isBungeeCordEnabled() {
        return config.getBoolean("cross-server.bungeecord.enabled", false);
    }

    public boolean isConfirmSellGui() {
        return config.getBoolean("general.confirm-sell-gui", true);
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
