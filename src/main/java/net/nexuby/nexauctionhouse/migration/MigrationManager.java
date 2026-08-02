package net.nexuby.nexauctionhouse.migration;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages migration operations from other auction house plugins.
 * Provides the registry of available migrators and orchestrates the
 * backup → validate → migrate → report workflow.
 */
public class MigrationManager {

    private final NexAuctionHouse plugin;
    private final Map<String, AbstractMigrator> migrators = new LinkedHashMap<>();
    private final AtomicBoolean migrationInProgress = new AtomicBoolean();
    private final File ledgerFile;
    private final YamlConfiguration ledger;

    public MigrationManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
        this.ledgerFile = new File(plugin.getDataFolder(), "migration-ledger.yml");
        this.ledger = YamlConfiguration.loadConfiguration(ledgerFile);
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
        return migrationInProgress.get();
    }

    /**
     * Executes a full migration: validate → backup → migrate → report.
     * Should be called from an async thread (Bukkit scheduler).
     *
     * @param migrator The migrator to run
     * @return The migration report, or null if validation/backup failed
     */
    public MigrationReport executeMigration(AbstractMigrator migrator) {
        if (!migrationInProgress.compareAndSet(false, true)) {
            return null;
        }

        try {
            String ledgerKey = "sources." + migrator.getSourceName().toLowerCase();
            if (ledger.contains(ledgerKey + ".status")) {
                plugin.getLogger().warning("[Migration] Refusing to run " + migrator.getSourceName()
                        + " again. Review migration-ledger.yml before retrying an attempted migration.");
                return null;
            }

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

            // Persist the attempt before the first target write. A crash cannot silently cause a replay.
            if (!writeLedgerState(ledgerKey, "in_progress")) {
                plugin.getLogger().warning("[Migration] Could not persist migration ledger; aborting safely.");
                return null;
            }

            // Step 3: Run the migration
            plugin.getLogger().info("[Migration] Starting migration from " + migrator.getSourceName() + "...");
            MigrationReport report = migrator.migrate();
            plugin.getLogger().info("[Migration] " + report.getSummary());

            if (report.getErrors() == 0) {
                writeLedgerState(ledgerKey, "completed");
            } else {
                plugin.getLogger().warning("[Migration] Errors occurred. The ledger remains in_progress "
                        + "to prevent an unsafe automatic replay.");
            }

            // Step 4: Reload active auctions to include migrated data
            plugin.getAuctionManager().loadActiveAuctions();

            return report;
        } finally {
            migrationInProgress.set(false);
        }
    }

    private boolean writeLedgerState(String ledgerKey, String status) {
        ledger.set(ledgerKey + ".status", status);
        ledger.set(ledgerKey + ".updated-at", System.currentTimeMillis());
        try {
            ledger.save(ledgerFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save migration ledger", e);
            return false;
        }
    }
}
