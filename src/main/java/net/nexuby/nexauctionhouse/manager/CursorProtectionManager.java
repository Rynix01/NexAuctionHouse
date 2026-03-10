package net.nexuby.nexauctionhouse.manager;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.database.MongoManager;
import net.nexuby.nexauctionhouse.util.ItemSerializer;
import org.bson.Document;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Tracks items that are actively on a player's cursor inside our GUIs.
 * If the server crashes before the inventory close event fires, the item
 * data is already persisted in the database and can be restored on next login.
 */
public class CursorProtectionManager {

    private final NexAuctionHouse plugin;

    // Players who currently have a tracked cursor item in the DB
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();

    public CursorProtectionManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    private boolean isMongo() {
        return plugin.getDatabaseManager().isUsingMongoDB();
    }

    private MongoManager mongo() {
        return plugin.getDatabaseManager().getMongoManager();
    }

    /**
     * Saves a cursor item to the database for crash protection.
     * Called when a player picks up an item inside our GUI.
     */
    public void trackCursorItem(UUID playerUuid, String playerName, ItemStack item) {
        if (item == null || item.getType().isAir()) return;

        String itemData = ItemSerializer.toBase64(item);
        if (itemData == null) return;

        // Remove any existing tracked item first
        clearTracked(playerUuid);

        if (isMongo()) {
            try {
                int id = mongo().getNextId("rescued_items");
                Document doc = new Document("_id", id)
                        .append("player_uuid", playerUuid.toString())
                        .append("player_name", playerName)
                        .append("item_data", itemData)
                        .append("reason", "CURSOR_PROTECTION")
                        .append("created_at", System.currentTimeMillis());
                mongo().rescuedItems().insertOne(doc);
                trackedPlayers.add(playerUuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save cursor item for crash protection (MongoDB)", e);
            }
            return;
        }

        String sql = "INSERT INTO rescued_items (player_uuid, player_name, item_data, reason, created_at) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, itemData);
            stmt.setString(4, "CURSOR_PROTECTION");
            stmt.setLong(5, System.currentTimeMillis());
            stmt.executeUpdate();
            trackedPlayers.add(playerUuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save cursor item for crash protection", e);
        }
    }

    /**
     * Removes the tracked cursor item from the database.
     * Called when the player's GUI closes normally and the item is safely returned.
     */
    public void clearTracked(UUID playerUuid) {
        if (!trackedPlayers.remove(playerUuid)) return;

        if (isMongo()) {
            try {
                mongo().rescuedItems().deleteMany(
                        Filters.and(
                                Filters.eq("player_uuid", playerUuid.toString()),
                                Filters.eq("reason", "CURSOR_PROTECTION")));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to clear tracked cursor item (MongoDB)", e);
            }
            return;
        }

        String sql = "DELETE FROM rescued_items WHERE player_uuid = ? AND reason = 'CURSOR_PROTECTION'";

        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to clear tracked cursor item", e);
        }
    }

    /**
     * Saves an item to the rescued_items table when a player disconnects
     * with a full inventory and an item cannot be returned normally.
     */
    public void saveRescuedItem(UUID playerUuid, String playerName, ItemStack item, String reason) {
        if (item == null || item.getType().isAir()) return;

        String itemData = ItemSerializer.toBase64(item);
        if (itemData == null) return;

        if (isMongo()) {
            try {
                int id = mongo().getNextId("rescued_items");
                Document doc = new Document("_id", id)
                        .append("player_uuid", playerUuid.toString())
                        .append("player_name", playerName)
                        .append("item_data", itemData)
                        .append("reason", reason)
                        .append("created_at", System.currentTimeMillis());
                mongo().rescuedItems().insertOne(doc);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save rescued item (MongoDB)", e);
            }
            return;
        }

        String sql = "INSERT INTO rescued_items (player_uuid, player_name, item_data, reason, created_at) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, itemData);
            stmt.setString(4, reason);
            stmt.setLong(5, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save rescued item", e);
        }
    }

    /**
     * Retrieves all rescued items for a player.
     */
    public List<RescuedItem> getRescuedItems(UUID playerUuid) {
        List<RescuedItem> items = new ArrayList<>();

        if (isMongo()) {
            try {
                for (Document doc : mongo().rescuedItems()
                        .find(Filters.eq("player_uuid", playerUuid.toString()))
                        .sort(Sorts.ascending("created_at"))) {
                    int id = doc.getInteger("_id");
                    String itemData = doc.getString("item_data");
                    String reason = doc.getString("reason");
                    ItemStack item = ItemSerializer.fromBase64(itemData);

                    if (item != null) {
                        items.add(new RescuedItem(id, item, reason));
                    } else {
                        deleteRescuedItem(id);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load rescued items (MongoDB)", e);
            }
            return items;
        }

        String sql = "SELECT id, item_data, reason FROM rescued_items WHERE player_uuid = ? ORDER BY created_at ASC";

        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String itemData = rs.getString("item_data");
                String reason = rs.getString("reason");
                ItemStack item = ItemSerializer.fromBase64(itemData);

                if (item != null) {
                    items.add(new RescuedItem(id, item, reason));
                } else {
                    // Corrupted data - clean it up
                    deleteRescuedItem(id);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load rescued items", e);
        }
        return items;
    }

    /**
     * Deletes a specific rescued item by ID.
     */
    public void deleteRescuedItem(int id) {
        if (isMongo()) {
            try {
                mongo().rescuedItems().deleteOne(Filters.eq("_id", id));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete rescued item (MongoDB)", e);
            }
            return;
        }

        String sql = "DELETE FROM rescued_items WHERE id = ?";

        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete rescued item", e);
        }
    }

    /**
     * Deletes all rescued items for a player.
     */
    public void deleteAllRescuedItems(UUID playerUuid) {
        if (isMongo()) {
            try {
                mongo().rescuedItems().deleteMany(Filters.eq("player_uuid", playerUuid.toString()));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete rescued items (MongoDB)", e);
            }
            return;
        }

        String sql = "DELETE FROM rescued_items WHERE player_uuid = ?";

        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete rescued items", e);
        }
    }

    /**
     * Saves all currently tracked cursor items during server shutdown.
     * This is a safety measure - normally the GUI close event handles cleanup.
     */
    public void saveAllTracked() {
        // The items are already in the DB from trackCursorItem().
        // We just log how many were still active.
        if (!trackedPlayers.isEmpty()) {
            plugin.getLogger().info("Server shutdown: " + trackedPlayers.size() + " cursor item(s) protected in database.");
        }
        trackedPlayers.clear();
    }

    public boolean isTracked(UUID playerUuid) {
        return trackedPlayers.contains(playerUuid);
    }

    /**
     * Simple holder for a rescued item + its DB id.
     */
    public record RescuedItem(int id, ItemStack itemStack, String reason) {}
}
