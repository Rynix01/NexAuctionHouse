package net.nexuby.nexauctionhouse.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class TransactionLog {

    private final int id;
    private final int auctionId;
    private final UUID sellerUuid;
    private final UUID buyerUuid;
    private final ItemStack itemStack;
    private final double price;
    private final double taxAmount;
    private final String action;
    private final long timestamp;

    public TransactionLog(int id, int auctionId, UUID sellerUuid, UUID buyerUuid,
                          ItemStack itemStack, double price, double taxAmount,
                          String action, long timestamp) {
        this.id = id;
        this.auctionId = auctionId;
        this.sellerUuid = sellerUuid;
        this.buyerUuid = buyerUuid;
        this.itemStack = itemStack;
        this.price = price;
        this.taxAmount = taxAmount;
        this.action = action;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public int getAuctionId() { return auctionId; }
    public UUID getSellerUuid() { return sellerUuid; }
    public UUID getBuyerUuid() { return buyerUuid; }
    public ItemStack getItemStack() { return itemStack; }
    public double getPrice() { return price; }
    public double getTaxAmount() { return taxAmount; }
    public String getAction() { return action; }
    public long getTimestamp() { return timestamp; }

    /**
     * Returns true if this player was involved (as seller or buyer).
     */
    public boolean involvesPlayer(UUID playerUuid) {
        return playerUuid.equals(sellerUuid) || playerUuid.equals(buyerUuid);
    }

    /**
     * Returns true if this was a sale action (SALE or AUCTION_COMPLETE).
     */
    public boolean isSale() {
        return "SALE".equals(action) || "AUCTION_COMPLETE".equals(action);
    }
}
