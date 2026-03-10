package net.nexuby.nexauctionhouse.database;

import com.mongodb.MongoWriteException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
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
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * MongoDB implementation of the auction data access layer.
 * Extends AuctionDAO and overrides all methods to use MongoDB instead of JDBC.
 */
public class MongoAuctionDAO extends AuctionDAO {

    private final NexAuctionHouse plugin;
    private final MongoManager mongo;

    public MongoAuctionDAO(NexAuctionHouse plugin, MongoManager mongo) {
        super(plugin);
        this.plugin = plugin;
        this.mongo = mongo;
    }

    // -- Auction CRUD operations --

    @Override
    public int insertAuction(AuctionItem item) {
        try {
            int id = mongo.getNextId("auctions");
            Document doc = new Document("_id", id)
                    .append("seller_uuid", item.getSellerUuid().toString())
                    .append("seller_name", item.getSellerName())
                    .append("item_data", ItemSerializer.toBase64(item.getItemStack()))
                    .append("price", item.getPrice())
                    .append("currency", item.getCurrency())
                    .append("tax_rate", item.getTaxRate())
                    .append("created_at", item.getCreatedAt())
                    .append("expires_at", item.getExpiresAt())
                    .append("status", item.getStatus().name())
                    .append("auction_type", item.getAuctionType().name())
                    .append("highest_bid", item.getHighestBid())
                    .append("highest_bidder_uuid", item.getHighestBidderUuid() != null ? item.getHighestBidderUuid().toString() : null)
                    .append("highest_bidder_name", item.getHighestBidderName())
                    .append("auto_relist", item.isAutoRelist())
                    .append("relist_count", item.getRelistCount())
                    .append("max_relists", item.getMaxRelists())
                    .append("is_bundle", item.isBundle())
                    .append("bundle_data", item.isBundle() ? ItemSerializer.bundleToBase64(item.getBundleItems()) : null);
            mongo.auctions().insertOne(doc);
            return id;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert auction (MongoDB)", e);
            return -1;
        }
    }

    @Override
    public List<AuctionItem> getActiveAuctions() {
        List<AuctionItem> auctions = new ArrayList<>();
        try {
            for (Document doc : mongo.auctions()
                    .find(Filters.eq("status", "ACTIVE"))
                    .sort(Sorts.descending("created_at"))) {
                AuctionItem item = parseAuction(doc);
                if (item != null) auctions.add(item);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load active auctions (MongoDB)", e);
        }
        return auctions;
    }

    @Override
    public List<AuctionItem> getAuctionsBySeller(UUID sellerUuid) {
        List<AuctionItem> auctions = new ArrayList<>();
        try {
            for (Document doc : mongo.auctions()
                    .find(Filters.and(
                            Filters.eq("seller_uuid", sellerUuid.toString()),
                            Filters.eq("status", "ACTIVE")))
                    .sort(Sorts.descending("created_at"))) {
                AuctionItem item = parseAuction(doc);
                if (item != null) auctions.add(item);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load auctions by seller (MongoDB)", e);
        }
        return auctions;
    }

    @Override
    public boolean updateAuctionStatus(int auctionId, AuctionStatus status) {
        try {
            return mongo.auctions().updateOne(
                    Filters.eq("_id", auctionId),
                    Updates.set("status", status.name())
            ).getModifiedCount() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update auction status (MongoDB)", e);
            return false;
        }
    }

    @Override
    public AuctionItem getAuctionById(int id) {
        try {
            Document doc = mongo.auctions().find(Filters.eq("_id", id)).first();
            if (doc != null) return parseAuction(doc);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get auction by id (MongoDB)", e);
        }
        return null;
    }

    @Override
    public boolean updateAuctionPrice(int auctionId, double price) {
        try {
            return mongo.auctions().updateOne(
                    Filters.eq("_id", auctionId),
                    Updates.set("price", price)
            ).getModifiedCount() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update auction price (MongoDB)", e);
            return false;
        }
    }

    @Override
    public boolean updateAuctionExpiry(int auctionId, long expiresAt) {
        try {
            return mongo.auctions().updateOne(
                    Filters.eq("_id", auctionId),
                    Updates.set("expires_at", expiresAt)
            ).getModifiedCount() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update auction expiry (MongoDB)", e);
            return false;
        }
    }

    // -- Expired items --

    @Override
    public void insertExpiredItem(UUID ownerUuid, String ownerName, ItemStack itemStack, String reason) {
        try {
            int id = mongo.getNextId("expired_items");
            Document doc = new Document("_id", id)
                    .append("owner_uuid", ownerUuid.toString())
                    .append("owner_name", ownerName)
                    .append("item_data", ItemSerializer.toBase64(itemStack))
                    .append("reason", reason)
                    .append("created_at", System.currentTimeMillis());
            mongo.expiredItems().insertOne(doc);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert expired item (MongoDB)", e);
        }
    }

    @Override
    public List<ExpiredItem> getExpiredItems(UUID ownerUuid) {
        List<ExpiredItem> items = new ArrayList<>();
        try {
            for (Document doc : mongo.expiredItems()
                    .find(Filters.eq("owner_uuid", ownerUuid.toString()))
                    .sort(Sorts.descending("created_at"))) {
                ExpiredItem item = parseExpiredItem(doc);
                if (item != null) items.add(item);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load expired items (MongoDB)", e);
        }
        return items;
    }

    @Override
    public boolean deleteExpiredItem(int id) {
        try {
            return mongo.expiredItems().deleteOne(Filters.eq("_id", id)).getDeletedCount() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete expired item (MongoDB)", e);
            return false;
        }
    }

    @Override
    public int deleteOldExpiredItems(long olderThan) {
        try {
            return (int) mongo.expiredItems().deleteMany(
                    Filters.lt("created_at", olderThan)
            ).getDeletedCount();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to clean old expired items (MongoDB)", e);
            return 0;
        }
    }

    // -- Transaction logs --

    @Override
    public void logTransaction(int auctionId, UUID sellerUuid, UUID buyerUuid,
                               ItemStack itemStack, double price, double taxAmount, String action) {
        try {
            int id = mongo.getNextId("transaction_logs");
            Document doc = new Document("_id", id)
                    .append("auction_id", auctionId)
                    .append("seller_uuid", sellerUuid.toString())
                    .append("buyer_uuid", buyerUuid != null ? buyerUuid.toString() : null)
                    .append("item_data", ItemSerializer.toBase64(itemStack))
                    .append("price", price)
                    .append("tax_amount", taxAmount)
                    .append("action", action)
                    .append("timestamp", System.currentTimeMillis());
            mongo.transactionLogs().insertOne(doc);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to log transaction (MongoDB)", e);
        }
    }

    // -- Pending revenue queue --

    @Override
    public void insertPendingRevenue(UUID playerUuid, String playerName, double amount,
                                     String currency, int sourceAuctionId, String itemName, String buyerName) {
        try {
            int id = mongo.getNextId("pending_revenue");
            Document doc = new Document("_id", id)
                    .append("player_uuid", playerUuid.toString())
                    .append("player_name", playerName)
                    .append("amount", amount)
                    .append("currency", currency)
                    .append("source_auction_id", sourceAuctionId)
                    .append("item_name", itemName)
                    .append("buyer_name", buyerName)
                    .append("created_at", System.currentTimeMillis());
            mongo.pendingRevenue().insertOne(doc);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert pending revenue (MongoDB)", e);
        }
    }

    @Override
    public List<PendingRevenue> getPendingRevenue(UUID playerUuid) {
        List<PendingRevenue> entries = new ArrayList<>();
        try {
            for (Document doc : mongo.pendingRevenue()
                    .find(Filters.eq("player_uuid", playerUuid.toString()))
                    .sort(Sorts.ascending("created_at"))) {
                entries.add(parsePendingRevenue(doc));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load pending revenue (MongoDB)", e);
        }
        return entries;
    }

    @Override
    public boolean deletePendingRevenue(int id) {
        try {
            return mongo.pendingRevenue().deleteOne(Filters.eq("_id", id)).getDeletedCount() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete pending revenue (MongoDB)", e);
            return false;
        }
    }

    @Override
    public int deleteAllPendingRevenue(UUID playerUuid) {
        try {
            return (int) mongo.pendingRevenue().deleteMany(
                    Filters.eq("player_uuid", playerUuid.toString())
            ).getDeletedCount();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete all pending revenue (MongoDB)", e);
            return 0;
        }
    }

    // -- Bid operations --

    @Override
    public boolean updateHighestBid(int auctionId, double amount, UUID bidderUuid, String bidderName) {
        try {
            return mongo.auctions().updateOne(
                    Filters.eq("_id", auctionId),
                    Updates.combine(
                            Updates.set("highest_bid", amount),
                            Updates.set("highest_bidder_uuid", bidderUuid.toString()),
                            Updates.set("highest_bidder_name", bidderName))
            ).getModifiedCount() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update highest bid (MongoDB)", e);
            return false;
        }
    }

    @Override
    public int insertBid(int auctionId, UUID bidderUuid, String bidderName, double amount) {
        try {
            int id = mongo.getNextId("bids");
            Document doc = new Document("_id", id)
                    .append("auction_id", auctionId)
                    .append("bidder_uuid", bidderUuid.toString())
                    .append("bidder_name", bidderName)
                    .append("amount", amount)
                    .append("timestamp", System.currentTimeMillis());
            mongo.bids().insertOne(doc);
            return id;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert bid (MongoDB)", e);
            return -1;
        }
    }

    @Override
    public List<Bid> getBidsByAuction(int auctionId) {
        List<Bid> bids = new ArrayList<>();
        try {
            for (Document doc : mongo.bids()
                    .find(Filters.eq("auction_id", auctionId))
                    .sort(Sorts.descending("amount"))) {
                bids.add(parseBid(doc));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load bids for auction #" + auctionId + " (MongoDB)", e);
        }
        return bids;
    }

    @Override
    public Bid getHighestBid(int auctionId) {
        try {
            Document doc = mongo.bids()
                    .find(Filters.eq("auction_id", auctionId))
                    .sort(Sorts.descending("amount"))
                    .first();
            if (doc != null) return parseBid(doc);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get highest bid for auction #" + auctionId + " (MongoDB)", e);
        }
        return null;
    }

    @Override
    public boolean deleteBidsByAuction(int auctionId) {
        try {
            return mongo.bids().deleteMany(
                    Filters.eq("auction_id", auctionId)
            ).getDeletedCount() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete bids for auction #" + auctionId + " (MongoDB)", e);
            return false;
        }
    }

    @Override
    public List<Bid> getUniqueBiddersByAuction(int auctionId) {
        List<Bid> bids = new ArrayList<>();
        try {
            // Sort by amount desc then group by bidder to get each bidder's highest bid
            List<Bson> pipeline = Arrays.asList(
                    Aggregates.match(Filters.eq("auction_id", auctionId)),
                    Aggregates.sort(Sorts.descending("amount")),
                    Aggregates.group("$bidder_uuid",
                            Accumulators.first("doc_id", "$_id"),
                            Accumulators.first("auction_id", "$auction_id"),
                            Accumulators.first("bidder_name", "$bidder_name"),
                            Accumulators.first("amount", "$amount"),
                            Accumulators.first("timestamp", "$timestamp"))
            );

            for (Document doc : mongo.bids().aggregate(pipeline)) {
                int id = doc.getInteger("doc_id");
                String bidderUuid = doc.getString("_id");
                String bidderName = doc.getString("bidder_name");
                double amount = doc.getDouble("amount");
                long timestamp = doc.getLong("timestamp");
                bids.add(new Bid(id, auctionId, UUID.fromString(bidderUuid), bidderName, amount, timestamp));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get unique bidders for auction #" + auctionId + " (MongoDB)", e);
        }
        return bids;
    }

    // -- Favorites operations --

    @Override
    public boolean addFavorite(UUID playerUuid, int auctionId) {
        try {
            int id = mongo.getNextId("favorites");
            Document doc = new Document("_id", id)
                    .append("player_uuid", playerUuid.toString())
                    .append("auction_id", auctionId)
                    .append("added_at", System.currentTimeMillis());
            mongo.favorites().insertOne(doc);
            return true;
        } catch (MongoWriteException e) {
            // Unique constraint violation = already favorited
            if (e.getError().getCode() == 11000) return false;
            plugin.getLogger().log(Level.SEVERE, "Failed to add favorite (MongoDB)", e);
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add favorite (MongoDB)", e);
            return false;
        }
    }

    @Override
    public boolean removeFavorite(UUID playerUuid, int auctionId) {
        try {
            return mongo.favorites().deleteOne(
                    Filters.and(
                            Filters.eq("player_uuid", playerUuid.toString()),
                            Filters.eq("auction_id", auctionId))
            ).getDeletedCount() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove favorite (MongoDB)", e);
            return false;
        }
    }

    @Override
    public boolean isFavorited(UUID playerUuid, int auctionId) {
        try {
            return mongo.favorites()
                    .find(Filters.and(
                            Filters.eq("player_uuid", playerUuid.toString()),
                            Filters.eq("auction_id", auctionId)))
                    .first() != null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check favorite (MongoDB)", e);
            return false;
        }
    }

    @Override
    public List<Integer> getFavoriteAuctionIds(UUID playerUuid) {
        List<Integer> ids = new ArrayList<>();
        try {
            for (Document doc : mongo.favorites()
                    .find(Filters.eq("player_uuid", playerUuid.toString()))
                    .sort(Sorts.descending("added_at"))) {
                ids.add(doc.getInteger("auction_id"));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load favorites (MongoDB)", e);
        }
        return ids;
    }

    @Override
    public int getFavoriteCount(UUID playerUuid) {
        try {
            return (int) mongo.favorites().countDocuments(
                    Filters.eq("player_uuid", playerUuid.toString()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to count favorites (MongoDB)", e);
            return 0;
        }
    }

    @Override
    public List<UUID> getPlayersWhoFavorited(int auctionId) {
        List<UUID> players = new ArrayList<>();
        try {
            for (Document doc : mongo.favorites()
                    .find(Filters.eq("auction_id", auctionId))) {
                players.add(UUID.fromString(doc.getString("player_uuid")));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get players who favorited (MongoDB)", e);
        }
        return players;
    }

    @Override
    public boolean deleteFavoritesByAuction(int auctionId) {
        try {
            return mongo.favorites().deleteMany(
                    Filters.eq("auction_id", auctionId)
            ).getDeletedCount() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete favorites by auction (MongoDB)", e);
            return false;
        }
    }

    // -- Auto-relist --

    @Override
    public boolean updateAutoRelistData(int auctionId, int relistCount, long newExpiresAt) {
        try {
            return mongo.auctions().updateOne(
                    Filters.eq("_id", auctionId),
                    Updates.combine(
                            Updates.set("relist_count", relistCount),
                            Updates.set("expires_at", newExpiresAt),
                            Updates.set("status", "ACTIVE"))
            ).getModifiedCount() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update auto-relist data (MongoDB)", e);
            return false;
        }
    }

    // -- Transaction history queries --

    @Override
    public List<TransactionLog> getPlayerHistory(UUID playerUuid, int limit) {
        List<TransactionLog> logs = new ArrayList<>();
        try {
            String uuid = playerUuid.toString();
            for (Document doc : mongo.transactionLogs()
                    .find(Filters.and(
                            Filters.or(
                                    Filters.eq("seller_uuid", uuid),
                                    Filters.eq("buyer_uuid", uuid)),
                            Filters.in("action", "SALE", "AUCTION_COMPLETE")))
                    .sort(Sorts.descending("timestamp"))
                    .limit(limit)) {
                TransactionLog log = parseTransactionLog(doc);
                if (log != null) logs.add(log);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player history (MongoDB)", e);
        }
        return logs;
    }

    @Override
    public int getPlayerTotalSales(UUID playerUuid) {
        try {
            return (int) mongo.transactionLogs().countDocuments(
                    Filters.and(
                            Filters.eq("seller_uuid", playerUuid.toString()),
                            Filters.in("action", "SALE", "AUCTION_COMPLETE")));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to count player sales (MongoDB)", e);
            return 0;
        }
    }

    @Override
    public double getPlayerTotalRevenue(UUID playerUuid) {
        try {
            List<Bson> pipeline = Arrays.asList(
                    Aggregates.match(Filters.and(
                            Filters.eq("seller_uuid", playerUuid.toString()),
                            Filters.in("action", "SALE", "AUCTION_COMPLETE"))),
                    Aggregates.group(null,
                            Accumulators.sum("total", new Document("$subtract", Arrays.asList("$price", "$tax_amount"))))
            );
            Document result = mongo.transactionLogs().aggregate(pipeline).first();
            if (result != null) return result.getDouble("total");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sum player revenue (MongoDB)", e);
        }
        return 0;
    }

    @Override
    public int getPlayerTotalPurchases(UUID playerUuid) {
        try {
            return (int) mongo.transactionLogs().countDocuments(
                    Filters.and(
                            Filters.eq("buyer_uuid", playerUuid.toString()),
                            Filters.in("action", "SALE", "AUCTION_COMPLETE")));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to count player purchases (MongoDB)", e);
            return 0;
        }
    }

    @Override
    public double getAveragePrice(String materialName, int days) {
        long since = System.currentTimeMillis() - (days * 86400000L);
        try {
            double total = 0;
            int count = 0;
            for (Document doc : mongo.transactionLogs()
                    .find(Filters.and(
                            Filters.in("action", "SALE", "AUCTION_COMPLETE"),
                            Filters.gt("timestamp", since)))) {
                ItemStack item = ItemSerializer.fromBase64(doc.getString("item_data"));
                if (item != null && item.getType().name().equalsIgnoreCase(materialName)) {
                    total += doc.getDouble("price");
                    count++;
                }
            }
            return count > 0 ? Math.round(total / count * 100.0) / 100.0 : 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to calculate average price (MongoDB)", e);
            return 0;
        }
    }

    @Override
    public Map<String, Double> getAllAveragePrices(int days) {
        Map<String, Double> result = new HashMap<>();
        long since = System.currentTimeMillis() - (days * 86400000L);
        try {
            Map<String, double[]> accum = new HashMap<>();
            for (Document doc : mongo.transactionLogs()
                    .find(Filters.and(
                            Filters.in("action", "SALE", "AUCTION_COMPLETE"),
                            Filters.gt("timestamp", since)))) {
                ItemStack item = ItemSerializer.fromBase64(doc.getString("item_data"));
                if (item != null) {
                    String mat = item.getType().name().toUpperCase();
                    double price = doc.getDouble("price");
                    accum.computeIfAbsent(mat, k -> new double[]{0, 0});
                    accum.get(mat)[0] += price;
                    accum.get(mat)[1] += 1;
                }
            }
            for (Map.Entry<String, double[]> entry : accum.entrySet()) {
                double avg = Math.round(entry.getValue()[0] / entry.getValue()[1] * 100.0) / 100.0;
                if (avg > 0) result.put(entry.getKey(), avg);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to calculate all average prices (MongoDB)", e);
        }
        return result;
    }

    // -- Admin statistics queries --

    @Override
    public int getSaleCountSince(long since) {
        try {
            return (int) mongo.transactionLogs().countDocuments(
                    Filters.and(
                            Filters.in("action", "SALE", "AUCTION_COMPLETE"),
                            Filters.gt("timestamp", since)));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to count sales since timestamp (MongoDB)", e);
            return 0;
        }
    }

    @Override
    public double getTotalVolumeSince(long since) {
        try {
            List<Bson> pipeline = Arrays.asList(
                    Aggregates.match(Filters.and(
                            Filters.in("action", "SALE", "AUCTION_COMPLETE"),
                            Filters.gt("timestamp", since))),
                    Aggregates.group(null, Accumulators.sum("total", "$price"))
            );
            Document result = mongo.transactionLogs().aggregate(pipeline).first();
            if (result != null) return result.getDouble("total");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sum sales volume (MongoDB)", e);
        }
        return 0;
    }

    @Override
    public TransactionLog getMostExpensiveSale() {
        try {
            Document doc = mongo.transactionLogs()
                    .find(Filters.in("action", "SALE", "AUCTION_COMPLETE"))
                    .sort(Sorts.descending("price"))
                    .first();
            if (doc != null) return parseTransactionLog(doc);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get most expensive sale (MongoDB)", e);
        }
        return null;
    }

    @Override
    public List<String[]> getTopSellers(int limit) {
        List<String[]> results = new ArrayList<>();
        try {
            List<Bson> pipeline = Arrays.asList(
                    Aggregates.match(Filters.in("action", "SALE", "AUCTION_COMPLETE")),
                    Aggregates.group("$seller_uuid",
                            Accumulators.sum("sale_count", 1),
                            Accumulators.sum("total_value", "$price")),
                    Aggregates.sort(Sorts.descending("sale_count")),
                    Aggregates.limit(limit)
            );
            for (Document doc : mongo.transactionLogs().aggregate(pipeline)) {
                String uuid = doc.getString("_id");
                String count = String.valueOf(doc.getInteger("sale_count"));
                String value = String.valueOf(doc.getDouble("total_value"));
                results.add(new String[]{uuid, count, value});
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get top sellers (MongoDB)", e);
        }
        return results;
    }

    // -- Notification Settings --

    @Override
    public NotificationSettings getNotificationSettings(UUID playerUuid) {
        try {
            Document doc = mongo.playerSettings()
                    .find(Filters.eq("_id", playerUuid.toString()))
                    .first();
            if (doc != null) {
                return new NotificationSettings(
                        playerUuid,
                        doc.getBoolean("notification_sale", true),
                        doc.getBoolean("notification_bid", true),
                        doc.getBoolean("sound_enabled", true),
                        doc.getBoolean("notification_login", true),
                        doc.getBoolean("notification_favorite", true));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load notification settings (MongoDB)", e);
        }
        return null;
    }

    @Override
    public void saveNotificationSettings(NotificationSettings settings) {
        try {
            Document doc = new Document("_id", settings.getPlayerUuid().toString())
                    .append("notification_sale", settings.isSaleNotifications())
                    .append("notification_bid", settings.isBidNotifications())
                    .append("sound_enabled", settings.isSoundEffects())
                    .append("notification_login", settings.isLoginNotifications())
                    .append("notification_favorite", settings.isFavoriteNotifications())
                    .append("updated_at", System.currentTimeMillis());
            mongo.playerSettings().replaceOne(
                    Filters.eq("_id", settings.getPlayerUuid().toString()),
                    doc,
                    new ReplaceOptions().upsert(true));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save notification settings (MongoDB)", e);
        }
    }

    @Override
    public void saveNotificationSettingsMySQL(NotificationSettings settings) {
        // MongoDB uses the same upsert method
        saveNotificationSettings(settings);
    }

    // -- Document parsing helpers --

    private AuctionItem parseAuction(Document doc) {
        try {
            int id = doc.getInteger("_id");
            UUID sellerUuid = UUID.fromString(doc.getString("seller_uuid"));
            String sellerName = doc.getString("seller_name");
            ItemStack itemStack = ItemSerializer.fromBase64(doc.getString("item_data"));
            double price = doc.getDouble("price");
            String currency = doc.getString("currency");
            if (currency == null || currency.isEmpty()) currency = "money";
            double taxRate = doc.getDouble("tax_rate");
            long createdAt = doc.getLong("created_at");
            long expiresAt = doc.getLong("expires_at");
            AuctionStatus status = AuctionStatus.valueOf(doc.getString("status"));

            AuctionType auctionType;
            try {
                String typeStr = doc.getString("auction_type");
                auctionType = (typeStr != null) ? AuctionType.valueOf(typeStr) : AuctionType.BIN;
            } catch (IllegalArgumentException ignored) {
                auctionType = AuctionType.BIN;
            }

            double highestBid = doc.getDouble("highest_bid") != null ? doc.getDouble("highest_bid") : 0;
            String bidderStr = doc.getString("highest_bidder_uuid");
            UUID highestBidderUuid = (bidderStr != null && !bidderStr.isEmpty()) ? UUID.fromString(bidderStr) : null;
            String highestBidderName = doc.getString("highest_bidder_name");

            if (itemStack == null) {
                plugin.getLogger().warning("Skipping auction #" + id + " - item data is corrupted.");
                return null;
            }

            AuctionItem item = new AuctionItem(id, sellerUuid, sellerName, itemStack, price, currency, taxRate,
                    createdAt, expiresAt, status, auctionType, highestBid, highestBidderUuid, highestBidderName);

            // Auto-relist fields
            Boolean autoRelist = doc.getBoolean("auto_relist");
            if (autoRelist != null) item.setAutoRelist(autoRelist);
            Integer relistCount = doc.getInteger("relist_count");
            if (relistCount != null) item.setRelistCount(relistCount);
            Integer maxRelists = doc.getInteger("max_relists");
            if (maxRelists != null) item.setMaxRelists(maxRelists);

            // Bundle fields
            Boolean isBundle = doc.getBoolean("is_bundle");
            if (isBundle != null && isBundle) {
                item.setBundle(true);
                String bundleData = doc.getString("bundle_data");
                if (bundleData != null) {
                    item.setBundleItems(ItemSerializer.bundleFromBase64(bundleData));
                }
            }

            return item;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Skipping auction - invalid data: " + e.getMessage());
            return null;
        }
    }

    private ExpiredItem parseExpiredItem(Document doc) {
        try {
            int id = doc.getInteger("_id");
            UUID ownerUuid = UUID.fromString(doc.getString("owner_uuid"));
            String ownerName = doc.getString("owner_name");
            ItemStack itemStack = ItemSerializer.fromBase64(doc.getString("item_data"));
            String reason = doc.getString("reason");
            long createdAt = doc.getLong("created_at");

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

    private PendingRevenue parsePendingRevenue(Document doc) {
        int id = doc.getInteger("_id");
        UUID playerUuid = UUID.fromString(doc.getString("player_uuid"));
        String playerName = doc.getString("player_name");
        double amount = doc.getDouble("amount");
        String currency = doc.getString("currency");
        int sourceAuctionId = doc.getInteger("source_auction_id");
        String itemName = doc.getString("item_name");
        String buyerName = doc.getString("buyer_name");
        long createdAt = doc.getLong("created_at");
        return new PendingRevenue(id, playerUuid, playerName, amount, currency,
                sourceAuctionId, itemName, buyerName, createdAt);
    }

    private Bid parseBid(Document doc) {
        int id = doc.getInteger("_id");
        int auctionId = doc.getInteger("auction_id");
        UUID bidderUuid = UUID.fromString(doc.getString("bidder_uuid"));
        String bidderName = doc.getString("bidder_name");
        double amount = doc.getDouble("amount");
        long timestamp = doc.getLong("timestamp");
        return new Bid(id, auctionId, bidderUuid, bidderName, amount, timestamp);
    }

    private TransactionLog parseTransactionLog(Document doc) {
        try {
            int id = doc.getInteger("_id");
            int auctionId = doc.getInteger("auction_id");
            UUID sellerUuid = UUID.fromString(doc.getString("seller_uuid"));
            String buyerStr = doc.getString("buyer_uuid");
            UUID buyerUuid = (buyerStr != null && !buyerStr.isEmpty()) ? UUID.fromString(buyerStr) : null;
            ItemStack itemStack = ItemSerializer.fromBase64(doc.getString("item_data"));
            double price = doc.getDouble("price");
            double taxAmount = doc.getDouble("tax_amount");
            String action = doc.getString("action");
            long timestamp = doc.getLong("timestamp");

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
}
