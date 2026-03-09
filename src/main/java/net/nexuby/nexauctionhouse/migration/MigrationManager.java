package net.nexuby.nexauctionhouse.migration;

import net.nexuby.nexauctionhouse.NexAuctionHouse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages migration operations from other auction house plugins.
 * Provides the registry of available migrators and orchestrates the
 * backup → validate → migrate → report workflow.
 */
public class MigrationManager {

    private final NexAuctionHouse plugin;
    private final Map<String, AbstractMigrator> migrators = new LinkedHashMap<>();
    private boolean migrationInProgress;

    public MigrationManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
        registerMigrators();
    }

    private void registerMigrators() {
        register(new AuctionHouseMigrator(plugin));
        register(new CrazyAuctionsMigrator(plugin));
        register(new ZAuctionHouseMigrator(plugin));
        register(new AuctionMasterMigrator(plugin));
    }

    private void register(AbstractMigrator migrator) {
        migrators.put(migrator.getSourceName().toLowerCase(), migrator);
    }

    /**
     * Returns the set of supported plugin names that can be migrated from.
     */
    public Set<String> getSupportedPlugins() {
        return migrators.keySet();
    }

    /**
     * Gets the display names of all supported plugins.
     */
    public String getSupportedPluginsList() {
        return String.join(", ", migrators.values().stream()
                .map(AbstractMigrator::getSourceName)
                .toList());
    }

    /**
     * Finds a migrator by plugin name (case-insensitive).
     */
    public AbstractMigrator getMigrator(String pluginName) {
        return migrators.get(pluginName.toLowerCase());
    }

    public boolean isMigrationInProgress() {
        return migrationInProgress;
    }

    /**
     * Executes a full migration: validate → backup → migrate → report.
     * Should be called from an async thread (Bukkit scheduler).
     *
     * @param migrator The migrator to run
     * @return The migration report, or null if validation/backup failed
     */
    public MigrationReport executeMigration(AbstractMigrator migrator) {
        if (migrationInProgress) {
            return null;
        }

        migrationInProgress = true;
        try {
            // Step 1: Validate source data exists
            String validationError = migrator.validate();
            if (validationError != null) {
                plugin.getLogger().warning("[Migration] Validation failed: " + validationError);
                return null;
            }

            // Step 2: Create backup
            if (!migrator.createBackup()) {
                plugin.getLogger().warning("[Migration] Backup creation failed, aborting migration.");
                return null;
            }

            // Step 3: Run the migration
            plugin.getLogger().info("[Migration] Starting migration from " + migrator.getSourceName() + "...");
            MigrationReport report = migrator.migrate();
            plugin.getLogger().info("[Migration] " + report.getSummary());

            // Step 4: Reload active auctions to include migrated data
            plugin.getAuctionManager().loadActiveAuctions();

            return report;
        } finally {
            migrationInProgress = false;
        }
    }
}
