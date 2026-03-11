package net.nexuby.nexauctionhouse.api.event;

import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a player places a bid on an auction item.
 * This event is cancellable — cancelling it will prevent the bid from being placed.
 * Fired after all validation checks pass but before economy withdrawal.
 */
public class BidPlaceEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player bidder;
    private final AuctionItem auctionItem;
    private final double bidAmount;
    private boolean cancelled;

    public BidPlaceEvent(Player bidder, AuctionItem auctionItem, double bidAmount) {
        this.bidder = bidder;
        this.auctionItem = auctionItem;
        this.bidAmount = bidAmount;
        this.cancelled = false;
    }

    /**
     * Returns the player placing the bid.
     */
    public Player getBidder() {
        return bidder;
    }

    /**
     * Returns the auction item being bid on.
     */
    public AuctionItem getAuctionItem() {
        return auctionItem;
    }

    /**
     * Returns the bid amount.
     */
    public double getBidAmount() {
        return bidAmount;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
