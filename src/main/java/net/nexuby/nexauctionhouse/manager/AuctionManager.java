package net.nexuby.nexauctionhouse.manager;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.config.ConfigManager;
import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.hook.DiscordWebhook;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionStatus;
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
    public int listItem(Player seller, ItemStack itemStack, double price) {
        ConfigManager config = plugin.getConfigManager();

        // Determine tax rate for this player
        double taxRate = getPlayerTaxRate(seller);

        // Determine auction duration for this player
        int durationHours = getPlayerAuctionDuration(seller);
        long now = System.currentTimeMillis();
        long expiresAt = now + (durationHours * 3600000L);

        AuctionItem auctionItem = new AuctionItem(
                0, seller.getUniqueId(), seller.getName(), itemStack,
                price, taxRate, now, expiresAt, AuctionStatus.ACTIVE
        );

        int id = dao.insertAuction(auctionItem);
        if (id > 0) {
            AuctionItem withId = new AuctionItem(
                    id, seller.getUniqueId(), seller.getName(), itemStack,
                    price, taxRate, now, expiresAt, AuctionStatus.ACTIVE
            );
            activeAuctions.put(id, withId);

            // Log the listing
            dao.logTransaction(id, seller.getUniqueId(), null, itemStack, price, 0, "LIST");

            // Discord notification
            discordWebhook.sendListingNotification(seller.getName(), itemStack, price);
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

        // Check buyer's balance
        if (!plugin.getEconomyManager().has(buyer, item.getPrice())) {
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
        plugin.getEconomyManager().withdraw(buyer, item.getPrice());

        double taxAmount = item.getTaxAmount();
        double sellerReceives = item.getSellerReceives();

        // Pay the seller (works even if offline)
        plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(item.getSellerUuid()), sellerReceives);

        // Give item to buyer
        buyer.getInventory().addItem(item.getItemStack());

        // Update database
        item.setStatus(AuctionStatus.SOLD);
        dao.updateAuctionStatus(auctionId, AuctionStatus.SOLD);
        dao.logTransaction(auctionId, item.getSellerUuid(), buyer.getUniqueId(),
                item.getItemStack(), item.getPrice(), taxAmount, "SALE");

        // Notify seller if online
        Player seller = Bukkit.getPlayer(item.getSellerUuid());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage(plugin.getLangManager().prefixed("auction.sold",
                    "{item}", getItemName(item.getItemStack()),
                    "{price}", plugin.getEconomyManager().format(item.getPrice()),
                    "{tax}", plugin.getEconomyManager().format(taxAmount)));
        }

        // Discord notification
        discordWebhook.sendSaleNotification(item.getSellerName(), buyer.getName(),
                item.getItemStack(), item.getPrice(), taxAmount);

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

        activeAuctions.remove(auctionId);
        item.setStatus(AuctionStatus.CANCELLED);
        dao.updateAuctionStatus(auctionId, AuctionStatus.CANCELLED);
        dao.logTransaction(auctionId, item.getSellerUuid(), null,
                item.getItemStack(), item.getPrice(), 0, isAdmin ? "ADMIN_CANCEL" : "CANCEL");

        // Try to give the item directly to the seller if they're online
        Player seller = Bukkit.getPlayer(item.getSellerUuid());
        if (seller != null && seller.isOnline() && seller.getInventory().firstEmpty() != -1) {
            seller.getInventory().addItem(item.getItemStack());
        } else {
            // Seller offline or inventory full - store for later pickup
            dao.insertExpiredItem(item.getSellerUuid(), item.getSellerName(), item.getItemStack(), "CANCELLED");
        }

        // Discord notification
        discordWebhook.sendCancelNotification(item.getSellerName(), item.getItemStack(), item.getPrice(), isAdmin);

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
            discordWebhook.sendPriceUpdateNotification(seller.getName(), item.getItemStack(), oldPrice, newPrice);
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
     */
    private void expireAuction(AuctionItem item) {
        activeAuctions.remove(item.getId());
        item.setStatus(AuctionStatus.EXPIRED);
        dao.updateAuctionStatus(item.getId(), AuctionStatus.EXPIRED);
        dao.insertExpiredItem(item.getSellerUuid(), item.getSellerName(), item.getItemStack(), "EXPIRED");
        dao.logTransaction(item.getId(), item.getSellerUuid(), null,
                item.getItemStack(), item.getPrice(), 0, "EXPIRE");
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
}
