package net.nexuby.nexauctionhouse;

import net.nexuby.nexauctionhouse.command.AuctionCommand;
import net.nexuby.nexauctionhouse.config.ConfigManager;
import net.nexuby.nexauctionhouse.config.GuiConfig;
import net.nexuby.nexauctionhouse.config.LangManager;
import net.nexuby.nexauctionhouse.database.DatabaseManager;
import net.nexuby.nexauctionhouse.economy.EconomyManager;
import net.nexuby.nexauctionhouse.hook.AuctionPlaceholders;
import net.nexuby.nexauctionhouse.hook.item.ItemHookManager;
import net.nexuby.nexauctionhouse.listener.ChatInputListener;
import net.nexuby.nexauctionhouse.listener.GuiListener;
import net.nexuby.nexauctionhouse.listener.PlayerListener;
import net.nexuby.nexauctionhouse.listener.BungeeCordListener;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.manager.CursorProtectionManager;
import net.nexuby.nexauctionhouse.manager.NotificationManager;
import net.nexuby.nexauctionhouse.manager.ThemeManager;
import net.nexuby.nexauctionhouse.migration.MigrationManager;
import net.nexuby.nexauctionhouse.redis.CrossServerManager;
import net.nexuby.nexauctionhouse.redis.RedisManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class NexAuctionHouse extends JavaPlugin {

    private static NexAuctionHouse instance;

    private ConfigManager configManager;
    private LangManager langManager;
    private GuiConfig guiConfig;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private AuctionManager auctionManager;
    private ItemHookManager itemHookManager;
    private CursorProtectionManager cursorProtectionManager;
    private NotificationManager notificationManager;
    private ThemeManager themeManager;
    private MigrationManager migrationManager;
    private RedisManager redisManager;
    private CrossServerManager crossServerManager;
    private BungeeCordListener bungeeCordListener;

    @Override
    public void onEnable() {
        instance = this;

        // Load configurations
        this.configManager = new ConfigManager(this);
        configManager.load();

        // Load language files
        this.langManager = new LangManager(this);
        langManager.load();

        // Load GUI configs
        this.guiConfig = new GuiConfig(this);
        guiConfig.load();

        // Setup database connection
        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("Failed to establish database connection! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Setup economy
        this.economyManager = new EconomyManager(this);
        if (!economyManager.setup()) {
            getLogger().severe("No economy provider available! At least one economy plugin must be enabled. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Register custom item hooks
        this.itemHookManager = new ItemHookManager(this);
        itemHookManager.registerAll();

        // Initialize cursor protection
        this.cursorProtectionManager = new CursorProtectionManager(this);

        // Initialize auction manager
        this.auctionManager = new AuctionManager(this);
        auctionManager.loadActiveAuctions();

        // Initialize notification manager
        this.notificationManager = new NotificationManager(this);

        // Initialize theme manager
        this.themeManager = new ThemeManager(this);
        themeManager.load();

        // Initialize migration manager
        this.migrationManager = new MigrationManager(this);

        // Initialize cross-server synchronization (Redis + BungeeCord)
        if (configManager.isCrossServerEnabled()) {
            if (databaseManager.isUsingSQLite()) {
                getLogger().severe("Cross-server mode requires MySQL or MongoDB! SQLite is not supported. Disabling cross-server sync.");
            } else {
                this.redisManager = new RedisManager(this);
                if (redisManager.connect()) {
                    this.crossServerManager = new CrossServerManager(this, redisManager);
                    crossServerManager.start();

                    if (configManager.isBungeeCordEnabled()) {
                        this.bungeeCordListener = new BungeeCordListener(this);
                        bungeeCordListener.register();
                    }

                    getLogger().info("Cross-server synchronization enabled.");
                } else {
                    getLogger().severe("Failed to connect to Redis! Cross-server sync disabled.");
                    this.redisManager = null;
                }
            }
        }

        // Register commands
        AuctionCommand auctionCommand = new AuctionCommand(this);
        getCommand("ah").setExecutor(auctionCommand);
        getCommand("ah").setTabCompleter(auctionCommand);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(this), this);

        // Hook into PlaceholderAPI if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AuctionPlaceholders(this).register();
            getLogger().info("PlaceholderAPI hook registered.");
        }

        getLogger().info("NexAuctionHouse v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save cursor protection data before shutdown
        if (cursorProtectionManager != null) {
            cursorProtectionManager.saveAllTracked();
        }

        if (auctionManager != null) {
            auctionManager.saveAll();
        }

        // Disconnect cross-server systems
        if (bungeeCordListener != null) {
            bungeeCordListener.unregister();
        }

        if (redisManager != null) {
            redisManager.disconnect();
        }

        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        getLogger().info("NexAuctionHouse has been disabled.");
    }

    public static NexAuctionHouse getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LangManager getLangManager() {
        return langManager;
    }

    public GuiConfig getGuiConfig() {
        return guiConfig;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public ItemHookManager getItemHookManager() {
        return itemHookManager;
    }

    public CursorProtectionManager getCursorProtectionManager() {
        return cursorProtectionManager;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public ThemeManager getThemeManager() {
        return themeManager;
    }

    public MigrationManager getMigrationManager() {
        return migrationManager;
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public CrossServerManager getCrossServerManager() {
        return crossServerManager;
    }

    public BungeeCordListener getBungeeCordListener() {
        return bungeeCordListener;
    }

    /**
     * Reloads all configuration files and refreshes the language cache.
     */
    public void reload() {
        configManager.load();
        langManager.load();
        guiConfig.load();
        themeManager.load();
        getLogger().info("All configurations have been reloaded.");
    }
}
