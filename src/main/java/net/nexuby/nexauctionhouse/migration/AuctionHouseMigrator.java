package net.nexuby.nexauctionhouse.migration;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionStatus;
import net.nexuby.nexauctionhouse.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Migrates data from klippa's AuctionHouse plugin.
 * Database: SQLite (data.db) with tables: auctions, expired
 * Items stored as Base64-serialized ItemStack.
 */
public class AuctionHouseMigrator extends AbstractMigrator {

    public AuctionHouseMigrator(NexAuctionHouse plugin) {
        super(plugin, "AuctionHouse");
    }

    @Override
    public String getSourceName() {
        return "AuctionHouse";
    }

    @Override
    public String validate() {
        File dbFile = resolvePluginFile("AuctionHouse", "data.db");
        if (!dbFile.exists()) {
            return "AuctionHouse database not found at: " + dbFile.getAbsolutePath();
        }
        return null;
    }

    @Override
    public MigrationReport migrate() {
        File dbFile = resolvePluginFile("AuctionHouse", "data.db");

        try (Connection conn = openSQLite(dbFile)) {
            migrateActiveAuctions(conn);
            migrateExpiredItems(conn);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "AuctionHouse migration failed", e);
            report.incrementErrors();
        }

        report.finish();
        return report;
    }

    private void migrateActiveAuctions(Connection conn) {
        // AuctionHouse stores: id, owner (uuid), ownerName, item (Base64), price, created, expires
        String sql = "SELECT * FROM auctions";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    UUID sellerUuid = UUID.fromString(rs.getString("owner"));
                    String sellerName = getStringOrDefault(rs, "ownerName", "Unknown");
                    String itemBase64 = rs.getString("item");
                    double price = rs.getDouble("price");
                    long createdAt = rs.getLong("created");
                    long expiresAt = rs.getLong("expires");

                    ItemStack itemStack = ItemSerializer.fromBase64(itemBase64);
                    if (itemStack == null) {
                        plugin.getLogger().warning("[Migration] Skipping AuctionHouse listing - corrupt item data");
                        report.incrementErrors();
                        continue;
                    }

                    // If already expired, store as expired item instead
                    if (System.currentTimeMillis() >= expiresAt) {
                        insertMigratedExpired(sellerUuid, sellerName, itemStack, "MIGRATED");
                        report.incrementExpired();
                        continue;
                    }

                    AuctionItem item = new AuctionItem(
                            0, sellerUuid, sellerName, itemStack,
                            price, "money", 0, createdAt, expiresAt, AuctionStatus.ACTIVE
                    );

                    int newId = insertMigratedAuction(item);
                    if (newId > 0) {
                        report.incrementAuctions();
                    } else {
                        report.incrementErrors();
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Migration] Error migrating AuctionHouse listing", e);
                    report.incrementErrors();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Migration] Could not read AuctionHouse auctions table", e);
            report.incrementErrors();
        }
    }

    private void migrateExpiredItems(Connection conn) {
        String sql = "SELECT * FROM expired";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    UUID ownerUuid = UUID.fromString(rs.getString("owner"));
                    String ownerName = getStringOrDefault(rs, "ownerName", "Unknown");
                    String itemBase64 = rs.getString("item");

                    ItemStack itemStack = ItemSerializer.fromBase64(itemBase64);
                    if (itemStack == null) {
                        report.incrementErrors();
                        continue;
                    }

                    insertMigratedExpired(ownerUuid, ownerName, itemStack, "MIGRATED");
                    report.incrementExpired();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Migration] Error migrating AuctionHouse expired item", e);
                    report.incrementErrors();
                }
            }
        } catch (Exception e) {
            // Table may not exist
            plugin.getLogger().info("[Migration] No expired items table found in AuctionHouse.");
        }
    }

    private String getStringOrDefault(ResultSet rs, String column, String defaultValue) {
        try {
            String val = rs.getString(column);
            return val != null ? val : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
