package net.nexuby.nexauctionhouse.model;

import java.util.UUID;

public class PendingRevenue {

    private final int id;
    private final UUID playerUuid;
    private final String playerName;
    private final double amount;
    private final String currency;
    private final int sourceAuctionId;
    private final String itemName;
    private final String buyerName;
    private final long createdAt;

    public PendingRevenue(int id, UUID playerUuid, String playerName, double amount,
                          String currency, int sourceAuctionId, String itemName,
                          String buyerName, long createdAt) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.amount = amount;
        this.currency = currency;
        this.sourceAuctionId = sourceAuctionId;
        this.itemName = itemName;
        this.buyerName = buyerName;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public double getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public int getSourceAuctionId() {
        return sourceAuctionId;
    }

    public String getItemName() {
        return itemName;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
