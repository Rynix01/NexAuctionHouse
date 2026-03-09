package net.nexuby.nexauctionhouse.migration;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionStatus;
import net.nexuby.nexauctionhouse.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Base class for all plugin migrators. Each subclass implements the
 * specific database reading logic for a different auction house plugin.
 */
public abstract class AbstractMigrator {

    protected final NexAuctionHouse plugin;
    protected final MigrationReport report;

    protected AbstractMigrator(NexAuctionHouse plugin, String sourceName) {
        this.plugin = plugin;
        this.report = new MigrationReport(sourceName);
    }

    /**
     * Returns the name of the source plugin (for display purposes).
     */
    public abstract String getSourceName();

    /**
     * Check if the source data files/database exist and can be read.
     * Returns null if valid, or an error message describing the problem.
     */
    public abstract String validate();

    /**
     * Run the migration. Called on an async thread.
     */
    public abstract MigrationReport migrate();

    /**
     * Helper: Opens a JDBC connection to a SQLite database file.
     */
    protected Connection openSQLite(File dbFile) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    /**
     * Helper: Inserts a migrated auction into NexAuctionHouse's database.
     */
    protected int insertMigratedAuction(AuctionItem item) {
        AuctionDAO dao = plugin.getAuctionManager().getDao();
        return dao.insertAuction(item);
    }

    /**
     * Helper: Inserts a migrated expired item into NexAuctionHouse's database.
     */
    protected void insertMigratedExpired(UUID ownerUuid, String ownerName, ItemStack itemStack, String reason) {
        AuctionDAO dao = plugin.getAuctionManager().getDao();
        dao.insertExpiredItem(ownerUuid, ownerName, itemStack, reason);
    }

    /**
     * Helper: Logs a migrated transaction.
     */
    protected void insertMigratedLog(int auctionId, UUID sellerUuid, UUID buyerUuid,
                                     ItemStack itemStack, double price, double tax, String action) {
        AuctionDAO dao = plugin.getAuctionManager().getDao();
        dao.logTransaction(auctionId, sellerUuid, buyerUuid, itemStack, price, tax, action);
    }

    /**
     * Resolves an SQLite file inside a plugin's data folder.
     */
    protected File resolvePluginFile(String pluginName, String fileName) {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        return new File(new File(pluginsDir, pluginName), fileName);
    }

    /**
     * Creates a backup copy of the target NexAuctionHouse database before migration.
     * Only applies to SQLite.
     */
    public boolean createBackup() {
        if (!plugin.getDatabaseManager().isUsingSQLite()) {
            // For MySQL, recommend manual backup via tools/mysqldump
            return true;
        }

        File dbFile = new File(plugin.getDataFolder(), "data.db");
        if (!dbFile.exists()) return true;

        String backupName = "data_backup_" + System.currentTimeMillis() + ".db";
        File backupFile = new File(plugin.getDataFolder(), backupName);

        try {
            java.nio.file.Files.copy(dbFile.toPath(), backupFile.toPath());
            plugin.getLogger().info("Database backup created: " + backupName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database backup", e);
            return false;
        }
    }

    public MigrationReport getReport() {
        return report;
    }
}
