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
 * Migrates data from zAuctionHouse plugin.
 * Database: SQLite (zAuctionHouse.db) with table: auctions
 * Items stored as Base64-serialized ItemStack.
 */
public class ZAuctionHouseMigrator extends AbstractMigrator {

    public ZAuctionHouseMigrator(NexAuctionHouse plugin) {
        super(plugin, "zAuctionHouse");
    }

    @Override
    public String getSourceName() {
        return "zAuctionHouse";
    }

    @Override
    public String validate() {
        File dbFile = resolvePluginFile("zAuctionHouse", "zAuctionHouse.db");
        if (!dbFile.exists()) {
            // Try alternate location
            File altFile = resolvePluginFile("zAuctionHouse", "data.db");
            if (!altFile.exists()) {
                return "zAuctionHouse database not found. Checked: "
                        + dbFile.getAbsolutePath() + " and " + altFile.getAbsolutePath();
            }
        }
        return null;
    }

    @Override
    public MigrationReport migrate() {
        File dbFile = resolvePluginFile("zAuctionHouse", "zAuctionHouse.db");
        if (!dbFile.exists()) {
            dbFile = resolvePluginFile("zAuctionHouse", "data.db");
        }

        try (Connection conn = openSQLite(dbFile)) {
            migrateAuctions(conn);
            migrateExpiredItems(conn);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "zAuctionHouse migration failed", e);
            report.incrementErrors();
        }

        report.finish();
        return report;
    }

    private void migrateAuctions(Connection conn) {
        // zAuctionHouse table: auctions (uuid, seller, item, price, start, end)
        String sql = "SELECT * FROM auctions";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    String uuidStr = findColumn(rs, "uuid", "seller_uuid", "owner");
                    UUID sellerUuid = UUID.fromString(uuidStr);
                    String sellerName = findColumnStr(rs, "seller", "seller_name", "owner_name");
                    if (sellerName == null) sellerName = "Unknown";

                    String itemBase64 = findColumnStr(rs, "item", "item_data", "itemstack");
                    double price = rs.getDouble("price");

                    long createdAt = findLong(rs, "start", "created", "created_at");
                    long expiresAt = findLong(rs, "end", "expires", "expires_at");

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
                    plugin.getLogger().log(Level.WARNING, "[Migration] Error migrating zAuctionHouse listing", e);
                    report.incrementErrors();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Migration] Could not read zAuctionHouse auctions table", e);
            report.incrementErrors();
        }
    }

    private void migrateExpiredItems(Connection conn) {
        String sql = "SELECT * FROM expired_items";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    String uuidStr = findColumn(rs, "uuid", "owner_uuid", "owner");
                    UUID ownerUuid = UUID.fromString(uuidStr);
                    String ownerName = findColumnStr(rs, "owner_name", "seller_name", "seller");
                    if (ownerName == null) ownerName = "Unknown";

                    String itemBase64 = findColumnStr(rs, "item", "item_data", "itemstack");
                    ItemStack itemStack = ItemSerializer.fromBase64(itemBase64);
                    if (itemStack == null) {
                        report.incrementErrors();
                        continue;
                    }

                    insertMigratedExpired(ownerUuid, ownerName, itemStack, "MIGRATED");
                    report.incrementExpired();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Migration] Error migrating zAuctionHouse expired item", e);
                    report.incrementErrors();
                }
            }
        } catch (Exception e) {
            // Table may not exist
            plugin.getLogger().info("[Migration] No expired_items table found in zAuctionHouse.");
        }
    }

    /**
     * Tries multiple possible column names and returns the first match.
     */
    private String findColumn(ResultSet rs, String... names) throws Exception {
        for (String name : names) {
            try {
                String val = rs.getString(name);
                if (val != null) return val;
            } catch (Exception ignored) {}
        }
        throw new IllegalStateException("Could not find column among: " + String.join(", ", names));
    }

    private String findColumnStr(ResultSet rs, String... names) {
        for (String name : names) {
            try {
                return rs.getString(name);
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
