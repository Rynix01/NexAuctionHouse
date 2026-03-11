package net.nexuby.nexauctionhouse.api;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Public API for NexAuctionHouse.
 * <p>
 * Provides read and write access to the auction house for third-party plugins.
 * Obtain the API instance via {@link #getInstance()}.
 * </p>
 *
 * <h3>Usage example:</h3>
 * <pre>{@code
 * NexAuctionHouseAPI api = NexAuctionHouseAPI.getInstance();
 * if (api != null) {
 *     Collection<AuctionItem> auctions = api.getActiveAuctions();
 *     PlayerStats stats = api.getPlayerStats(player.getUniqueId());
 * }
 * }</pre>
 *
 * <h3>Events:</h3>
 * <ul>
 *   <li>{@link net.nexuby.nexauctionhouse.api.event.AuctionListEvent} — fired before an item is listed (cancellable)</li>
 *   <li>{@link net.nexuby.nexauctionhouse.api.event.AuctionPurchaseEvent} — fired before a purchase (cancellable)</li>
 *   <li>{@link net.nexuby.nexauctionhouse.api.event.AuctionExpireEvent} — fired when an auction expires</li>
 *   <li>{@link net.nexuby.nexauctionhouse.api.event.AuctionCancelEvent} — fired when an auction is cancelled</li>
 *   <li>{@link net.nexuby.nexauctionhouse.api.event.BidPlaceEvent} — fired before a bid is placed (cancellable)</li>
 * </ul>
 */
public final class NexAuctionHouseAPI {

    private static NexAuctionHouseAPI instance;

    private final NexAuctionHouse plugin;

    public NexAuctionHouseAPI(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the API instance, or null if the plugin is not loaded.
     *
     * @return the API instance
     */
    public static NexAuctionHouseAPI getInstance() {
        return instance;
    }

    public static void setInstance(NexAuctionHouseAPI api) {
        instance = api;
    }

    // ---- Read Operations ----

    /**
     * Returns an unmodifiable collection of all currently active auctions.
     *
     * @return active auctions
     */
    public Collection<AuctionItem> getActiveAuctions() {
        return plugin.getAuctionManager().getActiveAuctionsList();
    }

    /**
     * Returns all active auctions listed by a specific player.
     *
     * @param playerUuid the player's UUID
     * @return list of auctions by the player
     */
    public List<AuctionItem> getAuctionsByPlayer(UUID playerUuid) {
        return plugin.getAuctionManager().getActiveAuctionsList().stream()
                .filter(item -> item.getSellerUuid().equals(playerUuid))
                .collect(Collectors.toList());
    }

    /**
     * Returns an auction by its ID, or null if not found or not active.
     *
     * @param auctionId the auction ID
     * @return the auction item, or null
     */
    public AuctionItem getAuction(int auctionId) {
        return plugin.getAuctionManager().getAuction(auctionId);
    }

    /**
     * Returns aggregated statistics for a player.
     * This method queries the database and may block briefly.
     *
     * @param playerUuid the player's UUID
     * @return player statistics
     */
    public PlayerStats getPlayerStats(UUID playerUuid) {
        AuctionDAO dao = plugin.getAuctionManager().getDao();
        int totalSales = dao.getPlayerTotalSales(playerUuid);
        double totalRevenue = dao.getPlayerTotalRevenue(playerUuid);
        int totalPurchases = dao.getPlayerTotalPurchases(playerUuid);
        int activeListings = plugin.getAuctionManager().getPlayerActiveListings(playerUuid);
        return new PlayerStats(playerUuid, totalSales, totalRevenue, totalPurchases, activeListings);
    }

    /**
     * Returns the cached average market price for a material (last 7 days).
     *
     * @param materialName the material name (e.g. "DIAMOND")
     * @return average price, or 0.0 if no data
     */
    public double getAveragePrice(String materialName) {
        return plugin.getAuctionManager().getAveragePrice(materialName);
    }

    // ---- Write Operations ----

    /**
     * Forces the creation of an auction listing, bypassing all checks
     * (blacklist, limits, balance). The item is taken from the parameters,
     * not from the player's inventory.
     * <p>
     * This fires an {@link net.nexuby.nexauctionhouse.api.event.AuctionListEvent}.
     * If the event is cancelled, the auction will not be created.
     * </p>
     *
     * @param seller   the player who will be listed as the seller
     * @param item     the item to list
     * @param price    the listing price
     * @param currency the currency name (e.g. "money", "gems")
     * @return the auction ID, or -1 if it failed or was cancelled
     */
    public int forceCreateAuction(Player seller, ItemStack item, double price, String currency) {
        return plugin.getAuctionManager().listItem(seller, item, price, currency);
    }

    /**
     * Forces the removal of an active auction by ID.
     * The item will be stored in the seller's expired items for pickup.
     * Any active bids will be refunded.
     *
     * @param auctionId the auction ID to remove
     * @return true if the auction was found and removed
     */
    public boolean forceRemoveAuction(int auctionId) {
        AuctionItem item = plugin.getAuctionManager().getAuction(auctionId);
        if (item == null) return false;

        // Use cancel with admin flag to bypass owner check
        return plugin.getAuctionManager().cancelAuction(null, auctionId, true);
    }

    /**
     * Checks if a specific material is blacklisted from the auction house.
     *
     * @param item the item to check
     * @return true if the item is blacklisted
     */
    public boolean isBlacklisted(ItemStack item) {
        return plugin.getAuctionManager().isBlacklisted(item);
    }

    /**
     * Returns the plugin version string.
     *
     * @return version string
     */
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }
}
