package net.nexuby.nexauctionhouse.model;

import java.util.UUID;

/**
 * Represents a single bid placed on an auction.
 */
public class Bid {

    private final int id;
    private final int auctionId;
    private final UUID bidderUuid;
    private final String bidderName;
    private final double amount;
    private final long timestamp;

    public Bid(int id, int auctionId, UUID bidderUuid, String bidderName, double amount, long timestamp) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderUuid = bidderUuid;
        this.bidderName = bidderName;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public int getAuctionId() {
        return auctionId;
    }

    public UUID getBidderUuid() {
        return bidderUuid;
    }

    public String getBidderName() {
        return bidderName;
    }

    public double getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
