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
 * Migrates data from AuctionMaster plugin.
 * Database: SQLite (AuctionMaster/database.db) with tables: auctions, expired_auctions
 * Items stored as Base64-serialized ItemStack.
 */
public class AuctionMasterMigrator extends AbstractMigrator {

    public AuctionMasterMigrator(NexAuctionHouse plugin) {
        super(plugin, "AuctionMaster");
    }

    @Override
    public String getSourceName() {
        return "AuctionMaster";
    }

    @Override
    public String validate() {
        File dbFile = resolvePluginFile("AuctionMaster", "database.db");
        if (!dbFile.exists()) {
            File altFile = resolvePluginFile("AuctionMaster", "data.db");
            if (!altFile.exists()) {
                return "AuctionMaster database not found. Checked: "
                        + dbFile.getAbsolutePath() + " and " + altFile.getAbsolutePath();
            }
        }
        return null;
    }

    @Override
    public MigrationReport migrate() {
        File dbFile = resolvePluginFile("AuctionMaster", "database.db");
        if (!dbFile.exists()) {
            dbFile = resolvePluginFile("AuctionMaster", "data.db");
        }

        try (Connection conn = openSQLite(dbFile)) {
            migrateAuctions(conn);
            migrateExpiredAuctions(conn);
            migrateLogs(conn);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "AuctionMaster migration failed", e);
            report.incrementErrors();
        }

        report.finish();
        return report;
    }

    private void migrateAuctions(Connection conn) {
        String sql = "SELECT * FROM auctions";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    String uuidStr = findColumnStr(rs, "seller_uuid", "uuid", "owner", "seller");
                    UUID sellerUuid = UUID.fromString(uuidStr);
                    String sellerName = findColumnStr(rs, "seller_name", "owner_name", "seller");
                    if (sellerName == null) sellerName = "Unknown";

                    String itemBase64 = findColumnStr(rs, "item_data", "item", "itemstack");
                    double price = rs.getDouble("price");

                    long createdAt = findLong(rs, "created_at", "created", "list_time");
                    long expiresAt = findLong(rs, "expires_at", "expires", "expire_time");

                    ItemStack itemStack = ItemSerializer.fromBase64(itemBase64);
                    if (itemStack == null) {
                        report.incrementErrors();
                        continue;
                    }

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
                    plugin.getLogger().log(Level.WARNING, "[Migration] Error migrating AuctionMaster listing", e);
                    report.incrementErrors();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Migration] Could not read AuctionMaster auctions table", e);
            report.incrementErrors();
        }
    }

    private void migrateExpiredAuctions(Connection conn) {
        String sql = "SELECT * FROM expired_auctions";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    String uuidStr = findColumnStr(rs, "seller_uuid", "uuid", "owner", "seller");
                    UUID ownerUuid = UUID.fromString(uuidStr);
                    String ownerName = findColumnStr(rs, "seller_name", "owner_name", "seller");
                    if (ownerName == null) ownerName = "Unknown";

                    String itemBase64 = findColumnStr(rs, "item_data", "item", "itemstack");
                    ItemStack itemStack = ItemSerializer.fromBase64(itemBase64);
                    if (itemStack == null) {
                        report.incrementErrors();
                        continue;
                    }

                    insertMigratedExpired(ownerUuid, ownerName, itemStack, "MIGRATED");
                    report.incrementExpired();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Migration] Error migrating AuctionMaster expired auction", e);
                    report.incrementErrors();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().info("[Migration] No expired_auctions table found in AuctionMaster.");
        }
    }

    private void migrateLogs(Connection conn) {
        String sql = "SELECT * FROM logs";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    String sellerStr = findColumnStr(rs, "seller_uuid", "seller", "uuid");
                    UUID sellerUuid = UUID.fromString(sellerStr);
                    String buyerStr = findColumnStr(rs, "buyer_uuid", "buyer");
                    UUID buyerUuid = buyerStr != null && !buyerStr.isEmpty() ? UUID.fromString(buyerStr) : null;

                    String itemBase64 = findColumnStr(rs, "item_data", "item", "itemstack");
                    double price = rs.getDouble("price");
                    String action = findColumnStr(rs, "action", "type", "log_type");
                    if (action == null) action = "SALE";

                    ItemStack itemStack = ItemSerializer.fromBase64(itemBase64);
                    if (itemStack == null) {
                        report.incrementErrors();
                        continue;
                    }

                    insertMigratedLog(0, sellerUuid, buyerUuid, itemStack, price, 0, "MIGRATED_" + action);
                    report.incrementLogs();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Migration] Error migrating AuctionMaster log entry", e);
                    report.incrementErrors();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().info("[Migration] No logs table found in AuctionMaster.");
        }
    }

    private String findColumnStr(ResultSet rs, String... names) {
        for (String name : names) {
            try {
                String val = rs.getString(name);
                if (val != null) return val;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private long findLong(ResultSet rs, String... names) {
        for (String name : names) {
            try {
                return rs.getLong(name);
            } catch (Exception ignored) {}
        }
        return System.currentTimeMillis();
    }
}
