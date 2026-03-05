package net.nexuby.nexauctionhouse.database;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionStatus;
import net.nexuby.nexauctionhouse.model.ExpiredItem;
import net.nexuby.nexauctionhouse.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
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
        String sql = "INSERT INTO auctions (seller_uuid, seller_name, item_data, price, tax_rate, created_at, expires_at, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, item.getSellerUuid().toString());
            stmt.setString(2, item.getSellerName());
            stmt.setString(3, ItemSerializer.toBase64(item.getItemStack()));
            stmt.setDouble(4, item.getPrice());
            stmt.setDouble(5, item.getTaxRate());
            stmt.setLong(6, item.getCreatedAt());
            stmt.setLong(7, item.getExpiresAt());
            stmt.setString(8, item.getStatus().name());
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

    // -- Parsing helpers --

    private AuctionItem parseAuction(ResultSet rs) throws SQLException {
        try {
            int id = rs.getInt("id");
            UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
            String sellerName = rs.getString("seller_name");
            ItemStack itemStack = ItemSerializer.fromBase64(rs.getString("item_data"));
            double price = rs.getDouble("price");
            double taxRate = rs.getDouble("tax_rate");
            long createdAt = rs.getLong("created_at");
            long expiresAt = rs.getLong("expires_at");
            AuctionStatus status = AuctionStatus.valueOf(rs.getString("status"));

            if (itemStack == null) {
                plugin.getLogger().warning("Skipping auction #" + id + " - item data is corrupted.");
                return null;
            }

            return new AuctionItem(id, sellerUuid, sellerName, itemStack, price, taxRate, createdAt, expiresAt, status);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Skipping auction - invalid data: " + e.getMessage());
            return null;
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
}
