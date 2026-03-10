package net.nexuby.nexauctionhouse.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import org.bson.Document;

import java.util.logging.Level;

public class MongoManager {

    private final NexAuctionHouse plugin;
    private MongoClient client;
    private MongoDatabase database;

    public MongoManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            String connectionString = plugin.getConfigManager().getMongoConnectionString();
            String databaseName = plugin.getConfigManager().getMongoDatabase();

            client = MongoClients.create(connectionString);
            database = client.getDatabase(databaseName);

            // Verify connection
            database.runCommand(new Document("ping", 1));

            createIndexes();

            plugin.getLogger().info("MongoDB connection established. (Database: " + databaseName + ")");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to MongoDB", e);
            return false;
        }
    }

    private void createIndexes() {
        auctions().createIndex(Indexes.ascending("status"));
        auctions().createIndex(Indexes.ascending("seller_uuid"));
        auctions().createIndex(Indexes.compoundIndex(
                Indexes.ascending("status"), Indexes.descending("created_at")));

        bids().createIndex(Indexes.ascending("auction_id"));
        bids().createIndex(Indexes.compoundIndex(
                Indexes.ascending("auction_id"), Indexes.descending("amount")));

        transactionLogs().createIndex(Indexes.ascending("seller_uuid"));
        transactionLogs().createIndex(Indexes.ascending("buyer_uuid"));
        transactionLogs().createIndex(Indexes.compoundIndex(
                Indexes.ascending("action"), Indexes.descending("timestamp")));

        expiredItems().createIndex(Indexes.ascending("owner_uuid"));

        pendingRevenue().createIndex(Indexes.ascending("player_uuid"));

        rescuedItems().createIndex(Indexes.ascending("player_uuid"));

        favorites().createIndex(
                Indexes.compoundIndex(Indexes.ascending("player_uuid"), Indexes.ascending("auction_id")),
                new IndexOptions().unique(true));
    }

    /**
     * Gets the next auto-increment ID for a given collection.
     * Uses a dedicated 'counters' collection with findAndModify.
     */
    public int getNextId(String collectionName) {
        Document result = database.getCollection("counters")
                .findOneAndUpdate(
                        Filters.eq("_id", collectionName),
                        Updates.inc("seq", 1),
                        new FindOneAndUpdateOptions()
                                .upsert(true)
                                .returnDocument(ReturnDocument.AFTER));
        return result.getInteger("seq");
    }

    public MongoCollection<Document> auctions() {
        return database.getCollection("auctions");
    }

    public MongoCollection<Document> bids() {
        return database.getCollection("bids");
    }

    public MongoCollection<Document> transactionLogs() {
        return database.getCollection("transaction_logs");
    }

    public MongoCollection<Document> expiredItems() {
        return database.getCollection("expired_items");
    }

    public MongoCollection<Document> pendingRevenue() {
        return database.getCollection("pending_revenue");
    }

    public MongoCollection<Document> rescuedItems() {
        return database.getCollection("rescued_items");
    }

    public MongoCollection<Document> favorites() {
        return database.getCollection("favorites");
    }

    public MongoCollection<Document> playerSettings() {
        return database.getCollection("player_settings");
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public void disconnect() {
        if (client != null) {
            client.close();
            plugin.getLogger().info("MongoDB connection closed.");
        }
    }
}
