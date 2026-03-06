package net.nexuby.nexauctionhouse.manager;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.config.ConfigManager;
import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.hook.DiscordWebhook;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionStatus;
import net.nexuby.nexauctionhouse.model.AuctionType;
import net.nexuby.nexauctionhouse.model.Bid;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionManager {

    private final NexAuctionHouse plugin;
    private final AuctionDAO dao;
    private final DiscordWebhook discordWebhook;

    // Cache of active auctions keyed by auction id
    private final Map<Integer, AuctionItem> activeAuctions = new ConcurrentHashMap<>();

    // Cache of average prices by material name
    private final Map<String, Double> avgPriceCache = new ConcurrentHashMap<>();
    private long lastStatsCacheRefresh = 0;

    public AuctionManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
        this.dao = new AuctionDAO(plugin);
        this.discordWebhook = new DiscordWebhook(plugin);
    }

    /**
     * Loads all active auctions from the database into memory.
     */
    public void loadActiveAuctions() {
        activeAuctions.clear();
        List<AuctionItem> auctions = dao.getActiveAuctions();

        for (AuctionItem item : auctions) {
            if (item.isExpired()) {
                // Handle freshly expired items
                expireAuction(item);
            } else {
                activeAuctions.put(item.getId(), item);
            }
        }

        plugin.getLogger().info("Loaded " + activeAuctions.size() + " active auctions.");
        startExpirationTask();
    }

    /**
     * Periodically checks for expired auctions every minute.
     */
    private void startExpirationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<AuctionItem> expired = new ArrayList<>();
                for (AuctionItem item : activeAuctions.values()) {
                    if (item.isExpired()) {
                        expired.add(item);
                    }
                }
                for (AuctionItem item : expired) {
                    expireAuction(item);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60, 20L * 60); // every 60 seconds
    }

    /**
     * Lists a new item on the auction house.
     * Returns the auction id, or -1 if it failed.
     */
    public int listItem(Player seller, ItemStack itemStack, double price, String currency) {
        ConfigManager config = plugin.getConfigManager();

        // Determine tax rate for this player
        double taxRate = getPlayerTaxRate(seller);

        // Determine auction duration for this player
        int durationHours = getPlayerAuctionDuration(seller);
        long now = System.currentTimeMillis();
        long expiresAt = now + (durationHours * 3600000L);

        AuctionItem auctionItem = new AuctionItem(
                0, seller.getUniqueId(), seller.getName(), itemStack,
                price, currency, taxRate, now, expiresAt, AuctionStatus.ACTIVE
        );

        int id = dao.insertAuction(auctionItem);
        if (id > 0) {
            AuctionItem withId = new AuctionItem(
                    id, seller.getUniqueId(), seller.getName(), itemStack,
                    price, currency, taxRate, now, expiresAt, AuctionStatus.ACTIVE
            );
            activeAuctions.put(id, withId);

            // Log the listing
            dao.logTransaction(id, seller.getUniqueId(), null, itemStack, price, 0, "LIST");

            // Discord notification
            discordWebhook.sendListingNotification(seller.getName(), itemStack, price, currency);
        }

        return id;
    }

    /**
     * Lists a new bid auction (auction type) on the auction house.
     * Returns the auction id, or -1 if it failed.
     */
    public int listBidItem(Player seller, ItemStack itemStack, double startingPrice, String currency) {
        double taxRate = getPlayerTaxRate(seller);
        int durationHours = plugin.getConfigManager().getBidDefaultDuration();
        long now = System.currentTimeMillis();
        long expiresAt = now + (durationHours * 3600000L);

        AuctionItem auctionItem = new AuctionItem(
                0, seller.getUniqueId(), seller.getName(), itemStack,
                startingPrice, currency, taxRate, now, expiresAt, AuctionStatus.ACTIVE,
                AuctionType.AUCTION, 0, null, null
        );

        int id = dao.insertAuction(auctionItem);
        if (id > 0) {
            AuctionItem withId = new AuctionItem(
                    id, seller.getUniqueId(), seller.getName(), itemStack,
                    startingPrice, currency, taxRate, now, expiresAt, AuctionStatus.ACTIVE,
                    AuctionType.AUCTION, 0, null, null
            );
            activeAuctions.put(id, withId);
            dao.logTransaction(id, seller.getUniqueId(), null, itemStack, startingPrice, 0, "LIST_AUCTION");
            discordWebhook.sendListingNotification(seller.getName(), itemStack, startingPrice, currency);
        }

        return id;
    }

    /**
     * Processes a purchase of an auction item.
     * Returns true if successful.
     */
    public boolean purchaseItem(Player buyer, int auctionId) {
        AuctionItem item = activeAuctions.get(auctionId);
        if (item == null || item.isExpired()) {
            return false;
        }

        // Prevents buying own items
        if (item.getSellerUuid().equals(buyer.getUniqueId())) {
            return false;
        }

        String currency = item.getCurrency();

        // Check buyer's balance
        if (!plugin.getEconomyManager().has(buyer, item.getPrice(), currency)) {
            return false;
        }

        // Check inventory space
        if (buyer.getInventory().firstEmpty() == -1) {
            return false;
        }

        // Remove from active list first to prevent double-buy
        if (activeAuctions.remove(auctionId) == null) {
            return false;
        }

        // Process economy
        plugin.getEconomyManager().withdraw(buyer, item.getPrice(), currency);

        double taxAmount = item.getTaxAmount();
        double sellerReceives = item.getSellerReceives();

        // Pay the seller - direct deposit if online, queue if offline
        Player seller = Bukkit.getPlayer(item.getSellerUuid());
        if (seller != null && seller.isOnline()) {
            plugin.getEconomyManager().deposit(seller, sellerReceives, currency);
        } else {
            // Seller is offline - queue revenue for delivery on login
            dao.insertPendingRevenue(item.getSellerUuid(), item.getSellerName(), sellerReceives,
                    currency, auctionId, getItemName(item.getItemStack()), buyer.getName());
        }

        // Give item to buyer
        buyer.getInventory().addItem(item.getItemStack());

        // Update database
        item.setStatus(AuctionStatus.SOLD);
        dao.updateAuctionStatus(auctionId, AuctionStatus.SOLD);
        dao.logTransaction(auctionId, item.getSellerUuid(), buyer.getUniqueId(),
                item.getItemStack(), item.getPrice(), taxAmount, "SALE");

        // Notify seller if online
        if (seller != null && seller.isOnline()) {
            if (plugin.getNotificationManager().canReceiveSaleNotification(seller.getUniqueId())) {
                seller.sendMessage(plugin.getLangManager().prefixed("auction.sold",
                        "{item}", getItemName(item.getItemStack()),
                        "{price}", plugin.getEconomyManager().format(item.getPrice(), currency),
                        "{tax}", plugin.getEconomyManager().format(taxAmount, currency)));
                plugin.getNotificationManager().playSaleSound(seller);
            }
        }

        // Discord notification
        discordWebhook.sendSaleNotification(item.getSellerName(), buyer.getName(),
                item.getItemStack(), item.getPrice(), taxAmount, currency);

        // Notify favorite watchers
        notifyFavoriteWatchers(item, "sold");

        return true;
    }

    /**
     * Places a bid on an auction item.
     * Validates amount, refunds previous highest bidder, applies anti-snipe.
     * Returns true if the bid was placed successfully.
     */
    public boolean placeBid(Player bidder, int auctionId, double amount) {
        AuctionItem item = activeAuctions.get(auctionId);
        if (item == null || item.isExpired() || !item.isBidAuction()) {
            return false;
        }

        // Cannot bid on own item
        if (item.getSellerUuid().equals(bidder.getUniqueId())) {
            return false;
        }

        // Cannot bid if already highest bidder
        if (bidder.getUniqueId().equals(item.getHighestBidderUuid())) {
            return false;
        }

        String currency = item.getCurrency();

        // Calculate minimum bid
        double minBid;
        double incrementPercent = plugin.getConfigManager().getBidMinIncrementPercent();
        if (item.getHighestBid() > 0) {
            minBid = item.getHighestBid() * (1 + incrementPercent / 100.0);
        } else {
            minBid = item.getPrice(); // starting price
        }

        if (amount < minBid) {
            return false;
        }

        // Check bidder's balance
        if (!plugin.getEconomyManager().has(bidder, amount, currency)) {
            return false;
        }

        // Withdraw from bidder
        plugin.getEconomyManager().withdraw(bidder, amount, currency);

        // Refund previous highest bidder
        UUID previousBidderUuid = item.getHighestBidderUuid();
        double previousBidAmount = item.getHighestBid();
        if (previousBidderUuid != null && previousBidAmount > 0) {
            Player previousBidder = Bukkit.getPlayer(previousBidderUuid);
            if (previousBidder != null && previousBidder.isOnline()) {
                plugin.getEconomyManager().deposit(previousBidder, previousBidAmount, currency);
                if (plugin.getNotificationManager().canReceiveBidNotification(previousBidderUuid)) {
                    previousBidder.sendMessage(plugin.getLangManager().prefixed("bid.outbid",
                            "{item}", getItemName(item.getItemStack()),
                            "{amount}", plugin.getEconomyManager().format(amount, currency),
                            "{bidder}", bidder.getName()));
                    plugin.getNotificationManager().playBidSound(previousBidder);
                }
            } else {
                // Queue refund for offline player
                dao.insertPendingRevenue(previousBidderUuid, item.getHighestBidderName(), previousBidAmount,
                        currency, auctionId, getItemName(item.getItemStack()), bidder.getName());
            }
        }

        // Update the auction with new highest bid
        item.setHighestBid(amount);
        item.setHighestBidderUuid(bidder.getUniqueId());
        item.setHighestBidderName(bidder.getName());
        dao.updateHighestBid(auctionId, amount, bidder.getUniqueId(), bidder.getName());
        dao.insertBid(auctionId, bidder.getUniqueId(), bidder.getName(), amount);
        dao.logTransaction(auctionId, item.getSellerUuid(), bidder.getUniqueId(),
                item.getItemStack(), amount, 0, "BID");

        // Anti-snipe: extend if bid is within the last X seconds before expiry
        int antiSnipeSeconds = plugin.getConfigManager().getAntiSnipeSeconds();
        long remaining = item.getRemainingTime();
        if (remaining > 0 && remaining < antiSnipeSeconds * 1000L) {
            long newExpiry = System.currentTimeMillis() + (antiSnipeSeconds * 1000L);
            item.setExpiresAt(newExpiry);
            dao.updateAuctionExpiry(auctionId, newExpiry);
        }

        // Notify seller if online
        Player seller = Bukkit.getPlayer(item.getSellerUuid());
        if (seller != null && seller.isOnline()) {
            if (plugin.getNotificationManager().canReceiveBidNotification(seller.getUniqueId())) {
                seller.sendMessage(plugin.getLangManager().prefixed("bid.new-bid-seller",
                        "{item}", getItemName(item.getItemStack()),
                        "{bidder}", bidder.getName(),
                        "{amount}", plugin.getEconomyManager().format(amount, currency)));
                plugin.getNotificationManager().playBidSound(seller);
            }
        }

        // Discord notification
        discordWebhook.sendBidNotification(bidder.getName(), item.getSellerName(),
                item.getItemStack(), amount, currency);

        return true;
    }

    /**
     * Cancels an auction and returns the item to the seller.
     */
    public boolean cancelAuction(Player requester, int auctionId, boolean isAdmin) {
        AuctionItem item = activeAuctions.get(auctionId);
        if (item == null) {
            return false;
        }

        // Only the seller or an admin can cancel
        if (!isAdmin && (requester == null || !item.getSellerUuid().equals(requester.getUniqueId()))) {
            return false;
        }

        // Bid auctions with active bids can only be cancelled by admin
        if (item.isBidAuction() && item.getHighestBid() > 0 && !isAdmin) {
            return false;
        }

        activeAuctions.remove(auctionId);
        item.setStatus(AuctionStatus.CANCELLED);
        dao.updateAuctionStatus(auctionId, AuctionStatus.CANCELLED);
        dao.logTransaction(auctionId, item.getSellerUuid(), null,
                item.getItemStack(), item.getPrice(), 0, isAdmin ? "ADMIN_CANCEL" : "CANCEL");

        // Refund highest bidder if this was a bid auction
        if (item.isBidAuction() && item.getHighestBidderUuid() != null && item.getHighestBid() > 0) {
            refundHighestBidder(item);
        }

        // Try to give the item directly to the seller if they're online
        Player seller = Bukkit.getPlayer(item.getSellerUuid());
        if (seller != null && seller.isOnline() && seller.getInventory().firstEmpty() != -1) {
            seller.getInventory().addItem(item.getItemStack());
        } else {
            // Seller offline or inventory full - store for later pickup
            dao.insertExpiredItem(item.getSellerUuid(), item.getSellerName(), item.getItemStack(), "CANCELLED");
        }

        // Clean up bids
        if (item.isBidAuction()) {
            dao.deleteBidsByAuction(auctionId);
        }

        // Discord notification
        discordWebhook.sendCancelNotification(item.getSellerName(), item.getItemStack(), item.getPrice(), isAdmin, item.getCurrency());

        // Notify favorite watchers
        notifyFavoriteWatchers(item, "cancelled");

        return true;
    }

    /**
     * Updates the price of an active auction.
     * Only the seller can update the price.
     */
    public boolean updatePrice(Player seller, int auctionId, double newPrice) {
        AuctionItem item = activeAuctions.get(auctionId);
        if (item == null || item.isExpired()) {
            return false;
        }

        // Cannot edit price of bid auctions
        if (item.isBidAuction()) {
            return false;
        }

        if (!item.getSellerUuid().equals(seller.getUniqueId())) {
            return false;
        }

        ConfigManager config = plugin.getConfigManager();
        if (newPrice < config.getMinPrice() || newPrice > config.getMaxPrice()) {
            return false;
        }

        double oldPrice = item.getPrice();
        item.setPrice(newPrice);

        if (dao.updateAuctionPrice(auctionId, newPrice)) {
            dao.logTransaction(auctionId, seller.getUniqueId(), null,
                    item.getItemStack(), newPrice, 0, "PRICE_UPDATE");
            discordWebhook.sendPriceUpdateNotification(seller.getName(), item.getItemStack(), oldPrice, newPrice, item.getCurrency());
            return true;
        }

        // Revert on failure
        item.setPrice(oldPrice);
        return false;
    }

    /**
     * Extends the duration of an active auction.
     * Only the seller can extend the duration.
     */
    public boolean extendDuration(Player seller, int auctionId, int additionalHours) {
        AuctionItem item = activeAuctions.get(auctionId);
        if (item == null || item.isExpired()) {
            return false;
        }

        if (!item.getSellerUuid().equals(seller.getUniqueId())) {
            return false;
        }

        int maxDuration = plugin.getConfigManager().getMaxAuctionDuration();
        long maxExpiresAt = item.getCreatedAt() + (maxDuration * 3600000L);
        long newExpiresAt = item.getExpiresAt() + (additionalHours * 3600000L);

        // Cap at max duration
        if (newExpiresAt > maxExpiresAt) {
            newExpiresAt = maxExpiresAt;
        }

        // Already at or past max
        if (newExpiresAt <= item.getExpiresAt()) {
            return false;
        }

        long oldExpiry = item.getExpiresAt();
        item.setExpiresAt(newExpiresAt);

        if (dao.updateAuctionExpiry(auctionId, newExpiresAt)) {
            dao.logTransaction(auctionId, seller.getUniqueId(), null,
                    item.getItemStack(), item.getPrice(), 0, "EXTEND");
            return true;
        }

        // Revert on failure
        item.setExpiresAt(oldExpiry);
        return false;
    }

    /**
     * Moves an expired auction out of active list and into expired items.
     * For bid auctions with a winner, completes the sale.
     */
    private void expireAuction(AuctionItem item) {
        activeAuctions.remove(item.getId());

        if (item.isBidAuction() && item.getHighestBidderUuid() != null && item.getHighestBid() > 0) {
            // Bid auction with a winner - complete the sale
            completeAuction(item);
        } else {
            // BIN auction expired with no buyer, or bid auction with no bids
            item.setStatus(AuctionStatus.EXPIRED);
            dao.updateAuctionStatus(item.getId(), AuctionStatus.EXPIRED);
            dao.insertExpiredItem(item.getSellerUuid(), item.getSellerName(), item.getItemStack(), "EXPIRED");
            dao.logTransaction(item.getId(), item.getSellerUuid(), null,
                    item.getItemStack(), item.getPrice(), 0, "EXPIRE");

            // Clean up bids if it was a bid auction with no bids
            if (item.isBidAuction()) {
                dao.deleteBidsByAuction(item.getId());
            }

            // Notify favorite watchers
            notifyFavoriteWatchers(item, "expired");
        }
    }

    /**
     * Completes a bid auction: gives the item to the winner and pays the seller.
     */
    private void completeAuction(AuctionItem item) {
        item.setStatus(AuctionStatus.SOLD);
        dao.updateAuctionStatus(item.getId(), AuctionStatus.SOLD);

        double salePrice = item.getHighestBid();
        double taxAmount = salePrice * (item.getTaxRate() / 100.0);
        double sellerReceives = salePrice - taxAmount;
        String currency = item.getCurrency();

        // Pay the seller
        Player seller = Bukkit.getPlayer(item.getSellerUuid());
        if (seller != null && seller.isOnline()) {
            plugin.getEconomyManager().deposit(seller, sellerReceives, currency);
            if (plugin.getNotificationManager().canReceiveSaleNotification(seller.getUniqueId())) {
                seller.sendMessage(plugin.getLangManager().prefixed("bid.auction-won-seller",
                        "{item}", getItemName(item.getItemStack()),
                        "{winner}", item.getHighestBidderName(),
                        "{price}", plugin.getEconomyManager().format(salePrice, currency),
                        "{tax}", plugin.getEconomyManager().format(taxAmount, currency)));
                plugin.getNotificationManager().playSaleSound(seller);
            }
        } else {
            dao.insertPendingRevenue(item.getSellerUuid(), item.getSellerName(), sellerReceives,
                    currency, item.getId(), getItemName(item.getItemStack()), item.getHighestBidderName());
        }

        // Give item to winner
        Player winner = Bukkit.getPlayer(item.getHighestBidderUuid());
        if (winner != null && winner.isOnline() && winner.getInventory().firstEmpty() != -1) {
            winner.getInventory().addItem(item.getItemStack());
            if (plugin.getNotificationManager().canReceiveSaleNotification(winner.getUniqueId())) {
                winner.sendMessage(plugin.getLangManager().prefixed("bid.auction-won-buyer",
                        "{item}", getItemName(item.getItemStack()),
                        "{price}", plugin.getEconomyManager().format(salePrice, currency)));
                plugin.getNotificationManager().playSaleSound(winner);
            }
        } else {
            // Winner offline or full inventory - store for later pickup
            dao.insertExpiredItem(item.getHighestBidderUuid(), item.getHighestBidderName(),
                    item.getItemStack(), "AUCTION_WON");
        }

        dao.logTransaction(item.getId(), item.getSellerUuid(), item.getHighestBidderUuid(),
                item.getItemStack(), salePrice, taxAmount, "AUCTION_COMPLETE");

        // Clean up bids
        dao.deleteBidsByAuction(item.getId());

        // Discord notification
        discordWebhook.sendAuctionWonNotification(item.getHighestBidderName(), item.getSellerName(),
                item.getItemStack(), salePrice, currency);

        // Notify favorite watchers
        notifyFavoriteWatchers(item, "sold");
    }

    /**
     * Refunds the highest bidder of a bid auction.
     */
    private void refundHighestBidder(AuctionItem item) {
        Player bidder = Bukkit.getPlayer(item.getHighestBidderUuid());
        String currency = item.getCurrency();
        if (bidder != null && bidder.isOnline()) {
            plugin.getEconomyManager().deposit(bidder, item.getHighestBid(), currency);
            bidder.sendMessage(plugin.getLangManager().prefixed("bid.refund",
                    "{item}", getItemName(item.getItemStack()),
                    "{amount}", plugin.getEconomyManager().format(item.getHighestBid(), currency)));
        } else {
            dao.insertPendingRevenue(item.getHighestBidderUuid(), item.getHighestBidderName(),
                    item.getHighestBid(), currency, item.getId(),
                    getItemName(item.getItemStack()), "REFUND");
        }
    }

    // -- Permission-based limit helpers --

    public int getPlayerListingLimit(Player player) {
        int limit = plugin.getConfigManager().getDefaultListingLimit();

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (perm.startsWith("nexauctions.limit.") && info.getValue()) {
                try {
                    int value = Integer.parseInt(perm.substring("nexauctions.limit.".length()));
                    limit = Math.max(limit, value);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return limit;
    }

    public int getPlayerActiveListings(UUID playerUuid) {
        int count = 0;
        for (AuctionItem item : activeAuctions.values()) {
            if (item.getSellerUuid().equals(playerUuid)) {
                count++;
            }
        }
        return count;
    }

    public int getPlayerAuctionDuration(Player player) {
        int hours = plugin.getConfigManager().getDefaultAuctionDuration();

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (perm.startsWith("nexauctions.time.") && info.getValue()) {
                try {
                    int value = Integer.parseInt(perm.substring("nexauctions.time.".length()));
                    hours = Math.max(hours, value);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Math.min(hours, plugin.getConfigManager().getMaxAuctionDuration());
    }

    public double getPlayerTaxRate(Player player) {
        if (!plugin.getConfigManager().isTaxEnabled()) {
            return 0;
        }
        if (player.hasPermission("nexauctions.bypass.tax")) {
            return 0;
        }

        double rate = plugin.getConfigManager().getDefaultTaxRate();

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (perm.startsWith("nexauctions.tax.") && info.getValue()) {
                try {
                    double value = Double.parseDouble(perm.substring("nexauctions.tax.".length()));
                    rate = Math.min(rate, value); // lower tax is a VIP perk
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return rate;
    }

    // -- Blacklist check --

    public boolean isBlacklisted(ItemStack itemStack) {
        ConfigManager config = plugin.getConfigManager();

        // Check material blacklist
        String materialName = itemStack.getType().name();
        if (config.getBlacklistedMaterials().contains(materialName)) {
            return true;
        }

        // Check custom item ID blacklist (e.g. "itemsadder:custom_sword")
        if (plugin.getItemHookManager() != null) {
            String customId = plugin.getItemHookManager().getCustomItemId(itemStack);
            if (customId != null && config.getBlacklistedCustomItems().contains(customId)) {
                return true;
            }
        }

        // Check lore keywords
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasLore()) {
            List<String> keywords = config.getBlacklistedLoreKeywords();
            for (net.kyori.adventure.text.Component loreLine : itemStack.getItemMeta().lore()) {
                String plainLore = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(loreLine);
                for (String keyword : keywords) {
                    if (plainLore.toLowerCase().contains(keyword.toLowerCase())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // -- Utility --

    public Collection<AuctionItem> getActiveAuctionsList() {
        return Collections.unmodifiableCollection(activeAuctions.values());
    }

    public AuctionItem getAuction(int id) {
        return activeAuctions.get(id);
    }

    public AuctionDAO getDao() {
        return dao;
    }

    public void saveAll() {
        // Active auctions are already persisted in the database,
        // this is just a safety checkpoint
        plugin.getLogger().info("Auction data saved. (" + activeAuctions.size() + " active auctions)");
    }

    public double getMinBid(AuctionItem item) {
        double incrementPercent = plugin.getConfigManager().getBidMinIncrementPercent();
        if (item.getHighestBid() > 0) {
            return Math.ceil(item.getHighestBid() * (1 + incrementPercent / 100.0) * 100) / 100.0;
        }
        return item.getPrice();
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    // -- Favorites --

    public boolean addFavorite(Player player, int auctionId) {
        int maxFavorites = plugin.getConfigManager().getMaxFavorites();
        int currentCount = dao.getFavoriteCount(player.getUniqueId());
        if (currentCount >= maxFavorites) {
            return false;
        }
        return dao.addFavorite(player.getUniqueId(), auctionId);
    }

    public boolean removeFavorite(Player player, int auctionId) {
        return dao.removeFavorite(player.getUniqueId(), auctionId);
    }

    public boolean isFavorited(UUID playerUuid, int auctionId) {
        return dao.isFavorited(playerUuid, auctionId);
    }

    public List<AuctionItem> getFavoriteAuctions(UUID playerUuid) {
        List<Integer> ids = dao.getFavoriteAuctionIds(playerUuid);
        List<AuctionItem> favorites = new ArrayList<>();
        for (int id : ids) {
            AuctionItem item = activeAuctions.get(id);
            if (item != null && !item.isExpired()) {
                favorites.add(item);
            }
        }
        return favorites;
    }

    /**
     * Notifies players who favorited an auction that it has been sold or cancelled.
     */
    public void notifyFavoriteWatchers(AuctionItem item, String reason) {
        List<UUID> watchers = dao.getPlayersWhoFavorited(item.getId());
        for (UUID watcherUuid : watchers) {
            // Don't notify the seller
            if (watcherUuid.equals(item.getSellerUuid())) continue;

            // Check if this player wants favorite notifications
            if (!plugin.getNotificationManager().canReceiveFavoriteNotification(watcherUuid)) continue;

            Player watcher = Bukkit.getPlayer(watcherUuid);
            if (watcher != null && watcher.isOnline()) {
                if ("sold".equals(reason)) {
                    watcher.sendMessage(plugin.getLangManager().prefixed("favorites.notify-sold",
                            "{item}", getItemName(item.getItemStack()),
                            "{seller}", item.getSellerName()));
                } else if ("cancelled".equals(reason)) {
                    watcher.sendMessage(plugin.getLangManager().prefixed("favorites.notify-cancelled",
                            "{item}", getItemName(item.getItemStack()),
                            "{seller}", item.getSellerName()));
                } else if ("expired".equals(reason)) {
                    watcher.sendMessage(plugin.getLangManager().prefixed("favorites.notify-expired",
                            "{item}", getItemName(item.getItemStack()),
                            "{seller}", item.getSellerName()));
                }
                plugin.getNotificationManager().playFavoriteSound(watcher);
            }
        }
        // Clean up favorites for this auction
        dao.deleteFavoritesByAuction(item.getId());
    }

    public static String getItemName(ItemStack itemStack) {
        // Try custom item hooks first
        NexAuctionHouse instance = NexAuctionHouse.getInstance();
        if (instance != null && instance.getItemHookManager() != null) {
            String customName = instance.getItemHookManager().getCustomItemName(itemStack);
            if (customName != null) return customName;
        }

        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(itemStack.getItemMeta().displayName());
        }
        // Format material name: DIAMOND_SWORD -> Diamond Sword
        String name = itemStack.getType().name().replace("_", " ");
        StringBuilder formatted = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!formatted.isEmpty()) formatted.append(" ");
            formatted.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return formatted.toString();
    }

    // -- Price History & Statistics --

    /**
     * Returns the cached average price for a material. Refreshes cache if stale.
     */
    public double getAveragePrice(String materialName) {
        refreshStatsCacheIfNeeded();
        return avgPriceCache.getOrDefault(materialName.toUpperCase(), 0.0);
    }

    /**
     * Refreshes the average price cache if it has expired.
     */
    private void refreshStatsCacheIfNeeded() {
        long cacheDuration = plugin.getConfigManager().getStatsCacheDurationMinutes() * 60000L;
        if (System.currentTimeMillis() - lastStatsCacheRefresh < cacheDuration) return;

        refreshStatsCache();
    }

    /**
     * Forces a refresh of the statistics cache.
     */
    public void refreshStatsCache() {
        avgPriceCache.clear();

        // Get all active auction materials and compute avg from transaction history
        Set<String> materials = new HashSet<>();
        for (AuctionItem item : activeAuctions.values()) {
            materials.add(item.getItemStack().getType().name());
        }

        for (String material : materials) {
            double avg = dao.getAveragePrice(material, 7);
            if (avg > 0) {
                avgPriceCache.put(material.toUpperCase(), avg);
            }
        }

        lastStatsCacheRefresh = System.currentTimeMillis();
    }
}
