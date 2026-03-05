package net.nexuby.nexauctionhouse;

import net.nexuby.nexauctionhouse.command.AuctionCommand;
import net.nexuby.nexauctionhouse.config.ConfigManager;
import net.nexuby.nexauctionhouse.config.GuiConfig;
import net.nexuby.nexauctionhouse.config.LangManager;
import net.nexuby.nexauctionhouse.database.DatabaseManager;
import net.nexuby.nexauctionhouse.economy.EconomyManager;
import net.nexuby.nexauctionhouse.listener.PlayerListener;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
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
            getLogger().severe("Vault not found! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize auction manager
        this.auctionManager = new AuctionManager(this);
        auctionManager.loadActiveAuctions();

        // Register commands
        AuctionCommand auctionCommand = new AuctionCommand(this);
        getCommand("ah").setExecutor(auctionCommand);
        getCommand("ah").setTabCompleter(auctionCommand);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        getLogger().info("NexAuctionHouse v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        if (auctionManager != null) {
            auctionManager.saveAll();
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

    /**
     * Reloads all configuration files and refreshes the language cache.
     */
    public void reload() {
        configManager.load();
        langManager.load();
        guiConfig.load();
        getLogger().info("All configurations have been reloaded.");
    }
}
