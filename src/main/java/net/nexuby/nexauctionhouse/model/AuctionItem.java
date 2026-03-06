package net.nexuby.nexauctionhouse.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionItem {

    private final int id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack itemStack;
    private double price;
    private final String currency;
    private final double taxRate;
    private final long createdAt;
    private long expiresAt;
    private AuctionStatus status;
    private final AuctionType auctionType;
    private double highestBid;
    private UUID highestBidderUuid;
    private String highestBidderName;
    private boolean autoRelist;
    private int relistCount;
    private int maxRelists;

    public AuctionItem(int id, UUID sellerUuid, String sellerName, ItemStack itemStack,
                       double price, String currency, double taxRate, long createdAt, long expiresAt, AuctionStatus status) {
        this(id, sellerUuid, sellerName, itemStack, price, currency, taxRate, createdAt, expiresAt, status, AuctionType.BIN, 0, null, null);
    }

    public AuctionItem(int id, UUID sellerUuid, String sellerName, ItemStack itemStack,
                       double price, String currency, double taxRate, long createdAt, long expiresAt, AuctionStatus status,
                       AuctionType auctionType, double highestBid, UUID highestBidderUuid, String highestBidderName) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemStack = itemStack;
        this.price = price;
        this.currency = currency;
        this.taxRate = taxRate;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.auctionType = auctionType;
        this.highestBid = highestBid;
        this.highestBidderUuid = highestBidderUuid;
        this.highestBidderName = highestBidderName;
        this.autoRelist = false;
        this.relistCount = 0;
        this.maxRelists = 0;
    }

    public int getId() {
        return id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public double getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public double getTaxRate() {
        return taxRate;
    }

    /**
     * Calculates the actual tax amount from the sale price.
     */
    public double getTaxAmount() {
        return price * (taxRate / 100.0);
    }

    /**
     * Returns the amount the seller receives after tax deduction.
     */
    public double getSellerReceives() {
        return price - getTaxAmount();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public long getRemainingTime() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public AuctionType getAuctionType() {
        return auctionType;
    }

    public boolean isBidAuction() {
        return auctionType == AuctionType.AUCTION;
    }

    public double getHighestBid() {
        return highestBid;
    }

    public void setHighestBid(double highestBid) {
        this.highestBid = highestBid;
    }

    public UUID getHighestBidderUuid() {
        return highestBidderUuid;
    }

    public void setHighestBidderUuid(UUID highestBidderUuid) {
        this.highestBidderUuid = highestBidderUuid;
    }

    public String getHighestBidderName() {
        return highestBidderName;
    }

    public void setHighestBidderName(String highestBidderName) {
        this.highestBidderName = highestBidderName;
    }

    /**
     * Returns the current effective price for display purposes.
     * For BIN auctions this is the fixed price.
     * For bid auctions this is the highest bid, or the starting price if no bids.
     */
    public double getCurrentPrice() {
        if (auctionType == AuctionType.AUCTION && highestBid > 0) {
            return highestBid;
        }
        return price;
    }

    public boolean isAutoRelist() {
        return autoRelist;
    }

    public void setAutoRelist(boolean autoRelist) {
        this.autoRelist = autoRelist;
    }

    public int getRelistCount() {
        return relistCount;
    }

    public void setRelistCount(int relistCount) {
        this.relistCount = relistCount;
    }

    public int getMaxRelists() {
        return maxRelists;
    }

    public void setMaxRelists(int maxRelists) {
        this.maxRelists = maxRelists;
    }
}
