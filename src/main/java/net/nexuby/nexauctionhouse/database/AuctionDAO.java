package net.nexuby.nexauctionhouse.database;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionStatus;
import net.nexuby.nexauctionhouse.model.AuctionType;
import net.nexuby.nexauctionhouse.model.Bid;
import net.nexuby.nexauctionhouse.model.ExpiredItem;
import net.nexuby.nexauctionhouse.model.NotificationSettings;
import net.nexuby.nexauctionhouse.model.PendingRevenue;
import net.nexuby.nexauctionhouse.model.TransactionLog;
import net.nexuby.nexauctionhouse.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class AuctionDAO {

    private final NexAuctionHouse plugin;

    public AuctionDAO(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    private Connection conn() {
        return plugin.getDatabaseManager().getConnection();
    }

    // -- Auction CRUD operations --

    public int insertAuction(AuctionItem item) {
        String sql = "INSERT INTO auctions (seller_uuid, seller_name, item_data, price, currency, tax_rate, created_at, expires_at, status, auction_type, highest_bid, highest_bidder_uuid, highest_bidder_name, auto_relist, relist_count, max_relists, is_bundle, bundle_data) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, item.getSellerUuid().toString());
            stmt.setString(2, item.getSellerName());
            stmt.setString(3, ItemSerializer.toBase64(item.getItemStack()));
            stmt.setDouble(4, item.getPrice());
            stmt.setString(5, item.getCurrency());
            stmt.setDouble(6, item.getTaxRate());
            stmt.setLong(7, item.getCreatedAt());
            stmt.setLong(8, item.getExpiresAt());
            stmt.setString(9, item.getStatus().name());
            stmt.setString(10, item.getAuctionType().name());
            stmt.setDouble(11, item.getHighestBid());
            stmt.setString(12, item.getHighestBidderUuid() != null ? item.getHighestBidderUuid().toString() : null);
            stmt.setString(13, item.getHighestBidderName());
            stmt.setBoolean(14, item.isAutoRelist());
            stmt.setInt(15, item.getRelistCount());
            stmt.setInt(16, item.getMaxRelists());
            stmt.setBoolean(17, item.isBundle());
            stmt.setString(18, item.isBundle() ? ItemSerializer.bundleToBase64(item.getBundleItems()) : null);
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert auction", e);
        }
        return -1;
    }

    public List<AuctionItem> getActiveAuctions() {
        List<AuctionItem> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = 'ACTIVE' ORDER BY created_at DESC";

        try (PreparedStatement stmt = conn().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                AuctionItem item = parseAuction(rs);
                if (item != null) {
                    auctions.add(item);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load active auctions", e);
        }
        return auctions;
    }

    public List<AuctionItem> getAuctionsBySeller(UUID sellerUuid) {
        List<AuctionItem> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE seller_uuid = ? AND status = 'ACTIVE' ORDER BY created_at DESC";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, sellerUuid.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                AuctionItem item = parseAuction(rs);
                if (item != null) {
                    auctions.add(item);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load auctions by seller", e);
        }
        return auctions;
    }

    public boolean updateAuctionStatus(int auctionId, AuctionStatus status) {
        String sql = "UPDATE auctions SET status = ? WHERE id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setInt(2, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update auction status", e);
            return false;
        }
    }

    public AuctionItem getAuctionById(int id) {
        String sql = "SELECT * FROM auctions WHERE id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return parseAuction(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get auction by id", e);
        }
        return null;
    }

    public boolean updateAuctionPrice(int auctionId, double price) {
        String sql = "UPDATE auctions SET price = ? WHERE id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setDouble(1, price);
            stmt.setInt(2, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update auction price", e);
            return false;
        }
    }

    public boolean updateAuctionExpiry(int auctionId, long expiresAt) {
        String sql = "UPDATE auctions SET expires_at = ? WHERE id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setLong(1, expiresAt);
            stmt.setInt(2, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update auction expiry", e);
            return false;
        }
    }

    // -- Expired items --

    public void insertExpiredItem(UUID ownerUuid, String ownerName, ItemStack itemStack, String reason) {
        String sql = "INSERT INTO expired_items (owner_uuid, owner_name, item_data, reason, created_at) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, ownerName);
            stmt.setString(3, ItemSerializer.toBase64(itemStack));
            stmt.setString(4, reason);
            stmt.setLong(5, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert expired item", e);
        }
    }

    public List<ExpiredItem> getExpiredItems(UUID ownerUuid) {
        List<ExpiredItem> items = new ArrayList<>();
        String sql = "SELECT * FROM expired_items WHERE owner_uuid = ? ORDER BY created_at DESC";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ExpiredItem item = parseExpiredItem(rs);
                if (item != null) {
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load expired items", e);
        }
        return items;
    }

    public boolean deleteExpiredItem(int id) {
        String sql = "DELETE FROM expired_items WHERE id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete expired item", e);
            return false;
        }
    }

    public int deleteOldExpiredItems(long olderThan) {
        String sql = "DELETE FROM expired_items WHERE created_at < ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setLong(1, olderThan);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to clean old expired items", e);
            return 0;
        }
    }

    // -- Transaction logs --

    public void logTransaction(int auctionId, UUID sellerUuid, UUID buyerUuid,
                               ItemStack itemStack, double price, double taxAmount, String action) {
        String sql = "INSERT INTO transaction_logs (auction_id, seller_uuid, buyer_uuid, item_data, price, tax_amount, action, timestamp) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setInt(1, auctionId);
            stmt.setString(2, sellerUuid.toString());
            stmt.setString(3, buyerUuid != null ? buyerUuid.toString() : null);
            stmt.setString(4, ItemSerializer.toBase64(itemStack));
            stmt.setDouble(5, price);
            stmt.setDouble(6, taxAmount);
            stmt.setString(7, action);
            stmt.setLong(8, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to log transaction", e);
        }
    }

    // -- Pending revenue queue --

    public void insertPendingRevenue(UUID playerUuid, String playerName, double amount,
                                     String currency, int sourceAuctionId, String itemName, String buyerName) {
        String sql = "INSERT INTO pending_revenue (player_uuid, player_name, amount, currency, source_auction_id, item_name, buyer_name, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerName);
            stmt.setDouble(3, amount);
            stmt.setString(4, currency);
            stmt.setInt(5, sourceAuctionId);
            stmt.setString(6, itemName);
            stmt.setString(7, buyerName);
            stmt.setLong(8, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert pending revenue", e);
        }
    }

    public List<PendingRevenue> getPendingRevenue(UUID playerUuid) {
        List<PendingRevenue> entries = new ArrayList<>();
        String sql = "SELECT * FROM pending_revenue WHERE player_uuid = ? ORDER BY created_at ASC";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                entries.add(parsePendingRevenue(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load pending revenue", e);
        }
        return entries;
    }

    public boolean deletePendingRevenue(int id) {
        String sql = "DELETE FROM pending_revenue WHERE id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete pending revenue", e);
            return false;
        }
    }

    public int deleteAllPendingRevenue(UUID playerUuid) {
        String sql = "DELETE FROM pending_revenue WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            return stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete all pending revenue", e);
            return 0;
        }
    }

    // -- Bid operations --

    public boolean updateHighestBid(int auctionId, double amount, UUID bidderUuid, String bidderName) {
        String sql = "UPDATE auctions SET highest_bid = ?, highest_bidder_uuid = ?, highest_bidder_name = ? WHERE id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setString(2, bidderUuid.toString());
            stmt.setString(3, bidderName);
            stmt.setInt(4, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update highest bid", e);
            return false;
        }
    }

    public int insertBid(int auctionId, UUID bidderUuid, String bidderName, double amount) {
        String sql = "INSERT INTO bids (auction_id, bidder_uuid, bidder_name, amount, timestamp) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, auctionId);
            stmt.setString(2, bidderUuid.toString());
            stmt.setString(3, bidderName);
            stmt.setDouble(4, amount);
            stmt.setLong(5, System.currentTimeMillis());
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert bid", e);
        }
        return -1;
    }

    public List<Bid> getBidsByAuction(int auctionId) {
        List<Bid> bids = new ArrayList<>();
        String sql = "SELECT * FROM bids WHERE auction_id = ? ORDER BY amount DESC";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setInt(1, auctionId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                bids.add(parseBid(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load bids for auction #" + auctionId, e);
        }
        return bids;
    }

    public Bid getHighestBid(int auctionId) {
        String sql = "SELECT * FROM bids WHERE auction_id = ? ORDER BY amount DESC LIMIT 1";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setInt(1, auctionId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return parseBid(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get highest bid for auction #" + auctionId, e);
        }
        return null;
    }

    public boolean deleteBidsByAuction(int auctionId) {
        String sql = "DELETE FROM bids WHERE auction_id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setInt(1, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete bids for auction #" + auctionId, e);
            return false;
        }
    }

    public List<Bid> getUniqueBiddersByAuction(int auctionId) {
        List<Bid> bids = new ArrayList<>();
        String sql = "SELECT b1.* FROM bids b1 INNER JOIN ("
                + "SELECT bidder_uuid, MAX(amount) as max_amount FROM bids WHERE auction_id = ? GROUP BY bidder_uuid"
                + ") b2 ON b1.bidder_uuid = b2.bidder_uuid AND b1.amount = b2.max_amount WHERE b1.auction_id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setInt(1, auctionId);
            stmt.setInt(2, auctionId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                bids.add(parseBid(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get unique bidders for auction #" + auctionId, e);
        }
        return bids;
    }

    // -- Favorites operations --

    public boolean addFavorite(UUID playerUuid, int auctionId) {
        String sql = "INSERT INTO favorites (player_uuid, auction_id, added_at) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, auctionId);
            stmt.setLong(3, System.currentTimeMillis());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            // UNIQUE constraint violation means already favorited - not an error
            if (e.getMessage() != null && (e.getMessage().contains("UNIQUE") || e.getMessage().contains("Duplicate"))) {
                return false;
            }
            plugin.getLogger().log(Level.SEVERE, "Failed to add favorite", e);
            return false;
        }
    }

    public boolean removeFavorite(UUID playerUuid, int auctionId) {
        String sql = "DELETE FROM favorites WHERE player_uuid = ? AND auction_id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove favorite", e);
            return false;
        }
    }

    public boolean isFavorited(UUID playerUuid, int auctionId) {
        String sql = "SELECT 1 FROM favorites WHERE player_uuid = ? AND auction_id = ? LIMIT 1";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, auctionId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check favorite", e);
            return false;
        }
    }

    public List<Integer> getFavoriteAuctionIds(UUID playerUuid) {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT auction_id FROM favorites WHERE player_uuid = ? ORDER BY added_at DESC";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ids.add(rs.getInt("auction_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load favorites for player", e);
        }
        return ids;
    }

    public int getFavoriteCount(UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM favorites WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to count favorites", e);
        }
        return 0;
    }

    public List<UUID> getPlayersWhoFavorited(int auctionId) {
        List<UUID> players = new ArrayList<>();
        String sql = "SELECT player_uuid FROM favorites WHERE auction_id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setInt(1, auctionId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                players.add(UUID.fromString(rs.getString("player_uuid")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get players who favorited auction #" + auctionId, e);
        }
        return players;
    }

    public boolean deleteFavoritesByAuction(int auctionId) {
        String sql = "DELETE FROM favorites WHERE auction_id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setInt(1, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete favorites for auction #" + auctionId, e);
            return false;
        }
    }

    // -- Parsing helpers --

    private AuctionItem parseAuction(ResultSet rs) throws SQLException {
        try {
            int id = rs.getInt("id");
            UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
            String sellerName = rs.getString("seller_name");
            ItemStack itemStack = ItemSerializer.fromBase64(rs.getString("item_data"));
            double price = rs.getDouble("price");
            String currency;
            try {
                currency = rs.getString("currency");
                if (currency == null || currency.isEmpty()) currency = "money";
            } catch (SQLException ignored) {
                currency = "money";
            }
            double taxRate = rs.getDouble("tax_rate");
            long createdAt = rs.getLong("created_at");
            long expiresAt = rs.getLong("expires_at");
            AuctionStatus status = AuctionStatus.valueOf(rs.getString("status"));

            AuctionType auctionType;
            try {
                String typeStr = rs.getString("auction_type");
                auctionType = (typeStr != null) ? AuctionType.valueOf(typeStr) : AuctionType.BIN;
            } catch (SQLException | IllegalArgumentException ignored) {
                auctionType = AuctionType.BIN;
            }

            double highestBid;
            UUID highestBidderUuid = null;
            String highestBidderName = null;
            try {
                highestBid = rs.getDouble("highest_bid");
                String bidderStr = rs.getString("highest_bidder_uuid");
                if (bidderStr != null && !bidderStr.isEmpty()) {
                    highestBidderUuid = UUID.fromString(bidderStr);
                }
                highestBidderName = rs.getString("highest_bidder_name");
            } catch (SQLException ignored) {
                highestBid = 0;
            }

            if (itemStack == null) {
                plugin.getLogger().warning("Skipping auction #" + id + " - item data is corrupted.");
                return null;
            }

            AuctionItem item = new AuctionItem(id, sellerUuid, sellerName, itemStack, price, currency, taxRate, createdAt, expiresAt, status,
                    auctionType, highestBid, highestBidderUuid, highestBidderName);
            applyAutoRelistFields(item, rs);
            applyBundleFields(item, rs);
            return item;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Skipping auction - invalid data: " + e.getMessage());
            return null;
        }
    }

    // -- Auto-relist data --

    private void applyAutoRelistFields(AuctionItem item, ResultSet rs) {
        try {
            item.setAutoRelist(rs.getBoolean("auto_relist"));
            item.setRelistCount(rs.getInt("relist_count"));
            item.setMaxRelists(rs.getInt("max_relists"));
        } catch (SQLException ignored) {
            // Column may not exist yet in older databases
        }
    }

    private void applyBundleFields(AuctionItem item, ResultSet rs) {
        try {
            boolean isBundle = rs.getBoolean("is_bundle");
            item.setBundle(isBundle);
            if (isBundle) {
                String bundleData = rs.getString("bundle_data");
                item.setBundleItems(ItemSerializer.bundleFromBase64(bundleData));
            }
        } catch (SQLException ignored) {
            // Column may not exist yet in older databases
        }
    }

    public boolean updateAutoRelistData(int auctionId, int relistCount, long newExpiresAt) {
        String sql = "UPDATE auctions SET relist_count = ?, expires_at = ?, status = 'ACTIVE' WHERE id = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setInt(1, relistCount);
            stmt.setLong(2, newExpiresAt);
            stmt.setInt(3, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update auto-relist data", e);
            return false;
        }
    }

    private ExpiredItem parseExpiredItem(ResultSet rs) throws SQLException {
        try {
            int id = rs.getInt("id");
            UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
            String ownerName = rs.getString("owner_name");
            ItemStack itemStack = ItemSerializer.fromBase64(rs.getString("item_data"));
            String reason = rs.getString("reason");
            long createdAt = rs.getLong("created_at");

            if (itemStack == null) {
                plugin.getLogger().warning("Skipping expired item #" + id + " - item data is corrupted.");
                return null;
            }

            return new ExpiredItem(id, ownerUuid, ownerName, itemStack, reason, createdAt);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Skipping expired item - invalid data: " + e.getMessage());
            return null;
        }
    }

    private PendingRevenue parsePendingRevenue(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String playerName = rs.getString("player_name");
        double amount = rs.getDouble("amount");
        String currency = rs.getString("currency");
        int sourceAuctionId = rs.getInt("source_auction_id");
        String itemName = rs.getString("item_name");
        String buyerName = rs.getString("buyer_name");
        long createdAt = rs.getLong("created_at");

        return new PendingRevenue(id, playerUuid, playerName, amount, currency,
                sourceAuctionId, itemName, buyerName, createdAt);
    }

    private Bid parseBid(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int auctionId = rs.getInt("auction_id");
        UUID bidderUuid = UUID.fromString(rs.getString("bidder_uuid"));
        String bidderName = rs.getString("bidder_name");
        double amount = rs.getDouble("amount");
        long timestamp = rs.getLong("timestamp");
        return new Bid(id, auctionId, bidderUuid, bidderName, amount, timestamp);
    }

    // -- Transaction history queries --

    /**
     * Gets recent transaction logs involving a specific player (as seller or buyer).
     * Only returns SALE, AUCTION_COMPLETE actions for history display.
     */
    public List<TransactionLog> getPlayerHistory(UUID playerUuid, int limit) {
        List<TransactionLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM transaction_logs WHERE (seller_uuid = ? OR buyer_uuid = ?) "
                + "AND action IN ('SALE', 'AUCTION_COMPLETE') ORDER BY timestamp DESC LIMIT ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setInt(3, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                TransactionLog log = parseTransactionLog(rs);
                if (log != null) logs.add(log);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player history", e);
        }
        return logs;
    }

    /**
     * Gets the total number of successful sales by a player.
     */
    public int getPlayerTotalSales(UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM transaction_logs WHERE seller_uuid = ? AND action IN ('SALE', 'AUCTION_COMPLETE')";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to count player sales", e);
        }
        return 0;
    }

    /**
     * Gets the total revenue earned by a player from sales.
     */
    public double getPlayerTotalRevenue(UUID playerUuid) {
        String sql = "SELECT COALESCE(SUM(price - tax_amount), 0) FROM transaction_logs WHERE seller_uuid = ? AND action IN ('SALE', 'AUCTION_COMPLETE')";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sum player revenue", e);
        }
        return 0;
    }

    /**
     * Gets the total number of purchases made by a player.
     */
    public int getPlayerTotalPurchases(UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM transaction_logs WHERE buyer_uuid = ? AND action IN ('SALE', 'AUCTION_COMPLETE')";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to count player purchases", e);
        }
        return 0;
    }

    /**
     * Gets the average sale price for a specific material over the last N days.
     */
    public double getAveragePrice(String materialName, int days) {
        long since = System.currentTimeMillis() - (days * 86400000L);
        String sql = "SELECT t.price, t.item_data FROM transaction_logs t "
                + "WHERE t.action IN ('SALE', 'AUCTION_COMPLETE') AND t.timestamp > ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setLong(1, since);
            ResultSet rs = stmt.executeQuery();

            double total = 0;
            int count = 0;
            while (rs.next()) {
                ItemStack item = ItemSerializer.fromBase64(rs.getString("item_data"));
                if (item != null && item.getType().name().equalsIgnoreCase(materialName)) {
                    total += rs.getDouble("price");
                    count++;
                }
            }
            return count > 0 ? Math.round(total / count * 100.0) / 100.0 : 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to calculate average price for " + materialName, e);
        }
        return 0;
    }

    /**
     * Computes average sale prices for ALL materials that have been sold in the last N days.
     * Returns a map of material name (uppercase) -> average price.
     */
    public Map<String, Double> getAllAveragePrices(int days) {
        Map<String, Double> result = new HashMap<>();
        long since = System.currentTimeMillis() - (days * 86400000L);
        String sql = "SELECT t.price, t.item_data FROM transaction_logs t "
                + "WHERE t.action IN ('SALE', 'AUCTION_COMPLETE') AND t.timestamp > ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setLong(1, since);
            ResultSet rs = stmt.executeQuery();

            Map<String, double[]> accum = new HashMap<>(); // [total, count]
            while (rs.next()) {
                ItemStack item = ItemSerializer.fromBase64(rs.getString("item_data"));
                if (item != null) {
                    String mat = item.getType().name().toUpperCase();
                    double price = rs.getDouble("price");
                    accum.computeIfAbsent(mat, k -> new double[]{0, 0});
                    accum.get(mat)[0] += price;
                    accum.get(mat)[1] += 1;
                }
            }

            for (Map.Entry<String, double[]> entry : accum.entrySet()) {
                double avg = Math.round(entry.getValue()[0] / entry.getValue()[1] * 100.0) / 100.0;
                if (avg > 0) {
                    result.put(entry.getKey(), avg);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to calculate all average prices", e);
        }
        return result;
    }

    // -- Admin statistics queries --

    /**
     * Gets total sale count in the last N hours.
     */
    public int getSaleCountSince(long since) {
        String sql = "SELECT COUNT(*) FROM transaction_logs WHERE action IN ('SALE', 'AUCTION_COMPLETE') AND timestamp > ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setLong(1, since);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to count sales since timestamp", e);
        }
        return 0;
    }

    /**
     * Gets total revenue volume since a given timestamp.
     */
    public double getTotalVolumeSince(long since) {
        String sql = "SELECT COALESCE(SUM(price), 0) FROM transaction_logs WHERE action IN ('SALE', 'AUCTION_COMPLETE') AND timestamp > ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setLong(1, since);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sum sales volume", e);
        }
        return 0;
    }

    /**
     * Gets the most expensive sale ever recorded.
     */
    public TransactionLog getMostExpensiveSale() {
        String sql = "SELECT * FROM transaction_logs WHERE action IN ('SALE', 'AUCTION_COMPLETE') ORDER BY price DESC LIMIT 1";

        try (PreparedStatement stmt = conn().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return parseTransactionLog(rs);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get most expensive sale", e);
        }
        return null;
    }

    /**
     * Gets top sellers by total sales count.
     */
    public List<String[]> getTopSellers(int limit) {
        List<String[]> results = new ArrayList<>();
        String sql = "SELECT seller_uuid, COUNT(*) as sale_count, SUM(price) as total_value "
                + "FROM transaction_logs WHERE action IN ('SALE', 'AUCTION_COMPLETE') "
                + "GROUP BY seller_uuid ORDER BY sale_count DESC LIMIT ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String uuid = rs.getString("seller_uuid");
                String count = String.valueOf(rs.getInt("sale_count"));
                String value = String.valueOf(rs.getDouble("total_value"));
                results.add(new String[]{uuid, count, value});
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get top sellers", e);
        }
        return results;
    }

    private TransactionLog parseTransactionLog(ResultSet rs) throws SQLException {
        try {
            int id = rs.getInt("id");
            int auctionId = rs.getInt("auction_id");
            UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
            String buyerStr = rs.getString("buyer_uuid");
            UUID buyerUuid = (buyerStr != null && !buyerStr.isEmpty()) ? UUID.fromString(buyerStr) : null;
            ItemStack itemStack = ItemSerializer.fromBase64(rs.getString("item_data"));
            double price = rs.getDouble("price");
            double taxAmount = rs.getDouble("tax_amount");
            String action = rs.getString("action");
            long timestamp = rs.getLong("timestamp");

            if (itemStack == null) {
                plugin.getLogger().warning("Skipping transaction log #" + id + " - item data is corrupted.");
                return null;
            }

            return new TransactionLog(id, auctionId, sellerUuid, buyerUuid, itemStack, price, taxAmount, action, timestamp);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Skipping transaction log - invalid data: " + e.getMessage());
            return null;
        }
    }

    // -- Notification Settings --

    public NotificationSettings getNotificationSettings(UUID playerUuid) {
        String sql = "SELECT * FROM player_settings WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new NotificationSettings(
                        playerUuid,
                        rs.getBoolean("notification_sale"),
                        rs.getBoolean("notification_bid"),
                        rs.getBoolean("sound_enabled"),
                        rs.getBoolean("notification_login"),
                        rs.getBoolean("notification_favorite")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load notification settings", e);
        }
        return null;
    }

    public void saveNotificationSettings(NotificationSettings settings) {
        String sql = "INSERT INTO player_settings (player_uuid, notification_sale, notification_bid, sound_enabled, notification_login, notification_favorite, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(player_uuid) DO UPDATE SET "
                + "notification_sale = excluded.notification_sale, "
                + "notification_bid = excluded.notification_bid, "
                + "sound_enabled = excluded.sound_enabled, "
                + "notification_login = excluded.notification_login, "
                + "notification_favorite = excluded.notification_favorite, "
                + "updated_at = excluded.updated_at";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, settings.getPlayerUuid().toString());
            stmt.setBoolean(2, settings.isSaleNotifications());
            stmt.setBoolean(3, settings.isBidNotifications());
            stmt.setBoolean(4, settings.isSoundEffects());
            stmt.setBoolean(5, settings.isLoginNotifications());
            stmt.setBoolean(6, settings.isFavoriteNotifications());
            stmt.setLong(7, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save notification settings", e);
        }
    }

    public void saveNotificationSettingsMySQL(NotificationSettings settings) {
        String sql = "INSERT INTO player_settings (player_uuid, notification_sale, notification_bid, sound_enabled, notification_login, notification_favorite, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "notification_sale = VALUES(notification_sale), "
                + "notification_bid = VALUES(notification_bid), "
                + "sound_enabled = VALUES(sound_enabled), "
                + "notification_login = VALUES(notification_login), "
                + "notification_favorite = VALUES(notification_favorite), "
                + "updated_at = VALUES(updated_at)";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, settings.getPlayerUuid().toString());
            stmt.setBoolean(2, settings.isSaleNotifications());
            stmt.setBoolean(3, settings.isBidNotifications());
            stmt.setBoolean(4, settings.isSoundEffects());
            stmt.setBoolean(5, settings.isLoginNotifications());
            stmt.setBoolean(6, settings.isFavoriteNotifications());
            stmt.setLong(7, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save notification settings (MySQL)", e);
        }
    }

    // -- Player Theme --

    public String getPlayerTheme(UUID playerUuid) {
        String sql = "SELECT theme FROM player_settings WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("theme");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player theme", e);
        }
        return null;
    }

    public void setPlayerTheme(UUID playerUuid, String theme) {
        String sql = plugin.getDatabaseManager().isUsingSQLite()
                ? "INSERT INTO player_settings (player_uuid, notification_sale, notification_bid, sound_enabled, notification_login, notification_favorite, updated_at, theme) "
                + "VALUES (?, 1, 1, 1, 1, 1, ?, ?) "
                + "ON CONFLICT(player_uuid) DO UPDATE SET theme = excluded.theme, updated_at = excluded.updated_at"
                : "INSERT INTO player_settings (player_uuid, notification_sale, notification_bid, sound_enabled, notification_login, notification_favorite, updated_at, theme) "
                + "VALUES (?, 1, 1, 1, 1, 1, ?, ?) "
                + "ON DUPLICATE KEY UPDATE theme = VALUES(theme), updated_at = VALUES(updated_at)";

        try (PreparedStatement stmt = conn().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, theme);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player theme", e);
        }
    }
}
