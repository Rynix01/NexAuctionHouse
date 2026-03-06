package net.nexuby.nexauctionhouse.database;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.config.ConfigManager;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;

public class DatabaseManager {

    private final NexAuctionHouse plugin;
    private Connection connection;
    private boolean usingSQLite;

    public DatabaseManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        ConfigManager config = plugin.getConfigManager();
        String type = config.getDatabaseType().toLowerCase();

        try {
            if (type.equals("mysql")) {
                connectMySQL(config);
                usingSQLite = false;
            } else {
                connectSQLite();
                usingSQLite = true;
            }

            createTables();
            migrateDatabase();
            plugin.getLogger().info("Database connection established. (" + (usingSQLite ? "SQLite" : "MySQL") + ")");
            return true;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to database", e);
            return false;
        }
    }

    private void connectSQLite() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), "data.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        this.connection = DriverManager.getConnection(url);

        // Enable WAL mode for better concurrent read performance
        try (PreparedStatement stmt = connection.prepareStatement("PRAGMA journal_mode=WAL")) {
            stmt.execute();
        }
    }

    private void connectMySQL(ConfigManager config) throws SQLException {
        String host = config.getMySQLHost();
        int port = config.getMySQLPort();
        String database = config.getMySQLDatabase();
        String username = config.getMySQLUsername();
        String password = config.getMySQLPassword();
        boolean ssl = config.getMySQLSSL();

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + ssl
                + "&autoReconnect=true"
                + "&characterEncoding=UTF-8";

        this.connection = DriverManager.getConnection(url, username, password);
    }

    private void createTables() throws SQLException {
        // Primary auction table
        String auctionsTable = "CREATE TABLE IF NOT EXISTS auctions ("
                + "id INTEGER PRIMARY KEY " + (usingSQLite ? "AUTOINCREMENT" : "AUTO_INCREMENT") + ","
                + "seller_uuid VARCHAR(36) NOT NULL,"
                + "seller_name VARCHAR(16) NOT NULL,"
                + "item_data LONGTEXT NOT NULL,"
                + "price DOUBLE NOT NULL,"
                + "currency VARCHAR(32) NOT NULL DEFAULT 'money',"
                + "tax_rate DOUBLE NOT NULL DEFAULT 0,"
                + "created_at BIGINT NOT NULL,"
                + "expires_at BIGINT NOT NULL,"
                + "status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'"
                + ")";

        // Expired/uncollected items waiting for pickup
        String expiredTable = "CREATE TABLE IF NOT EXISTS expired_items ("
                + "id INTEGER PRIMARY KEY " + (usingSQLite ? "AUTOINCREMENT" : "AUTO_INCREMENT") + ","
                + "owner_uuid VARCHAR(36) NOT NULL,"
                + "owner_name VARCHAR(16) NOT NULL,"
                + "item_data LONGTEXT NOT NULL,"
                + "reason VARCHAR(32) NOT NULL,"
                + "created_at BIGINT NOT NULL"
                + ")";

        // Transaction log table
        String logsTable = "CREATE TABLE IF NOT EXISTS transaction_logs ("
                + "id INTEGER PRIMARY KEY " + (usingSQLite ? "AUTOINCREMENT" : "AUTO_INCREMENT") + ","
                + "auction_id INTEGER NOT NULL,"
                + "seller_uuid VARCHAR(36) NOT NULL,"
                + "buyer_uuid VARCHAR(36),"
                + "item_data LONGTEXT NOT NULL,"
                + "price DOUBLE NOT NULL,"
                + "tax_amount DOUBLE NOT NULL DEFAULT 0,"
                + "action VARCHAR(16) NOT NULL,"
                + "timestamp BIGINT NOT NULL"
                + ")";

        // Pending revenue queue for offline players
        String revenueTable = "CREATE TABLE IF NOT EXISTS pending_revenue ("
                + "id INTEGER PRIMARY KEY " + (usingSQLite ? "AUTOINCREMENT" : "AUTO_INCREMENT") + ","
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(16) NOT NULL,"
                + "amount DOUBLE NOT NULL,"
                + "currency VARCHAR(32) NOT NULL DEFAULT 'money',"
                + "source_auction_id INTEGER NOT NULL,"
                + "item_name VARCHAR(128) NOT NULL,"
                + "buyer_name VARCHAR(16) NOT NULL,"
                + "created_at BIGINT NOT NULL"
                + ")";

        // Rescued items - cursor/crash protection
        String rescuedTable = "CREATE TABLE IF NOT EXISTS rescued_items ("
                + "id INTEGER PRIMARY KEY " + (usingSQLite ? "AUTOINCREMENT" : "AUTO_INCREMENT") + ","
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(16) NOT NULL,"
                + "item_data LONGTEXT NOT NULL,"
                + "reason VARCHAR(32) NOT NULL,"
                + "created_at BIGINT NOT NULL"
                + ")";

        // Bids table for auction (bid) system
        String bidsTable = "CREATE TABLE IF NOT EXISTS bids ("
                + "id INTEGER PRIMARY KEY " + (usingSQLite ? "AUTOINCREMENT" : "AUTO_INCREMENT") + ","
                + "auction_id INTEGER NOT NULL,"
                + "bidder_uuid VARCHAR(36) NOT NULL,"
                + "bidder_name VARCHAR(16) NOT NULL,"
                + "amount DOUBLE NOT NULL,"
                + "timestamp BIGINT NOT NULL"
                + ")";

        // Favorites table for watchlist system
        String favoritesTable = "CREATE TABLE IF NOT EXISTS favorites ("
                + "id INTEGER PRIMARY KEY " + (usingSQLite ? "AUTOINCREMENT" : "AUTO_INCREMENT") + ","
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "auction_id INTEGER NOT NULL,"
                + "added_at BIGINT NOT NULL,"
                + "UNIQUE(player_uuid, auction_id)"
                + ")";

        // Player notification settings
        String settingsTable = "CREATE TABLE IF NOT EXISTS player_settings ("
                + "player_uuid VARCHAR(36) PRIMARY KEY,"
                + "notification_sale BOOLEAN NOT NULL DEFAULT 1,"
                + "notification_bid BOOLEAN NOT NULL DEFAULT 1,"
                + "sound_enabled BOOLEAN NOT NULL DEFAULT 1,"
                + "notification_login BOOLEAN NOT NULL DEFAULT 1,"
                + "notification_favorite BOOLEAN NOT NULL DEFAULT 1,"
                + "updated_at BIGINT NOT NULL"
                + ")";

        try (PreparedStatement stmt1 = connection.prepareStatement(auctionsTable);
             PreparedStatement stmt2 = connection.prepareStatement(expiredTable);
             PreparedStatement stmt3 = connection.prepareStatement(logsTable);
             PreparedStatement stmt4 = connection.prepareStatement(revenueTable);
             PreparedStatement stmt5 = connection.prepareStatement(rescuedTable);
             PreparedStatement stmt6 = connection.prepareStatement(bidsTable);
             PreparedStatement stmt7 = connection.prepareStatement(favoritesTable);
             PreparedStatement stmt8 = connection.prepareStatement(settingsTable)) {
            stmt1.executeUpdate();
            stmt2.executeUpdate();
            stmt3.executeUpdate();
            stmt4.executeUpdate();
            stmt5.executeUpdate();
            stmt6.executeUpdate();
            stmt7.executeUpdate();
            stmt8.executeUpdate();
        }
    }

    /**
     * Adds missing columns to existing databases (safe migration).
     */
    private void migrateDatabase() throws SQLException {
        migrateColumn("currency", "VARCHAR(32) NOT NULL DEFAULT 'money'");
        migrateColumn("auction_type", "VARCHAR(16) NOT NULL DEFAULT 'BIN'");
        migrateColumn("highest_bid", "DOUBLE NOT NULL DEFAULT 0");
        migrateColumn("highest_bidder_uuid", "VARCHAR(36) DEFAULT NULL");
        migrateColumn("highest_bidder_name", "VARCHAR(16) DEFAULT NULL");
    }

    private void migrateColumn(String columnName, String columnDef) {
        try {
            String checkSql = usingSQLite
                    ? "PRAGMA table_info(auctions)"
                    : "SHOW COLUMNS FROM auctions LIKE '" + columnName + "'";

            boolean hasColumn = false;

            try (PreparedStatement stmt = connection.prepareStatement(checkSql);
                 var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (usingSQLite) {
                        if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                            hasColumn = true;
                            break;
                        }
                    } else {
                        hasColumn = true;
                        break;
                    }
                }
            }

            if (!hasColumn) {
                String alterSql = "ALTER TABLE auctions ADD COLUMN " + columnName + " " + columnDef;
                try (PreparedStatement stmt = connection.prepareStatement(alterSql)) {
                    stmt.executeUpdate();
                }
                plugin.getLogger().info("Database migrated: added '" + columnName + "' column to auctions table.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database migration check failed for column: " + columnName, e);
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to validate database connection", e);
        }
        return connection;
    }

    public boolean isUsingSQLite() {
        return usingSQLite;
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error while closing database connection", e);
        }
    }
}
