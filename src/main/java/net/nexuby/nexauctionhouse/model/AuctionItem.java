package net.nexuby.nexauctionhouse.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionItem {

    private final int id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack itemStack;
    private double price;
    private final double taxRate;
    private final long createdAt;
    private long expiresAt;
    private AuctionStatus status;

    public AuctionItem(int id, UUID sellerUuid, String sellerName, ItemStack itemStack,
                       double price, double taxRate, long createdAt, long expiresAt, AuctionStatus status) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemStack = itemStack;
        this.price = price;
        this.taxRate = taxRate;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
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
}
