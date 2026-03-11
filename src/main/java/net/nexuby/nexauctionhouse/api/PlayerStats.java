package net.nexuby.nexauctionhouse.api;

import java.util.UUID;

/**
 * Represents aggregated statistics for a player's auction house activity.
 */
public class PlayerStats {

    private final UUID playerUuid;
    private final int totalSales;
    private final double totalRevenue;
    private final int totalPurchases;
    private final int activeListings;

    public PlayerStats(UUID playerUuid, int totalSales, double totalRevenue, int totalPurchases, int activeListings) {
        this.playerUuid = playerUuid;
        this.totalSales = totalSales;
        this.totalRevenue = totalRevenue;
        this.totalPurchases = totalPurchases;
        this.activeListings = activeListings;
    }

    /**
     * Returns the player's UUID.
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Returns the total number of items this player has sold.
     */
    public int getTotalSales() {
        return totalSales;
    }

    /**
     * Returns the total revenue earned from sales (after tax).
     */
    public double getTotalRevenue() {
        return totalRevenue;
    }

    /**
     * Returns the total number of items this player has purchased.
     */
    public int getTotalPurchases() {
        return totalPurchases;
    }

    /**
     * Returns the current number of active listings by this player.
     */
    public int getActiveListings() {
        return activeListings;
    }
}
