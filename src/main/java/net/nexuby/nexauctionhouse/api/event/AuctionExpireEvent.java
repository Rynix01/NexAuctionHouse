package net.nexuby.nexauctionhouse.api.event;

import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when an auction item expires naturally (timer runs out).
 * This event is NOT cancellable — it is informational only.
 */
public class AuctionExpireEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final AuctionItem auctionItem;
    private final boolean hadWinner;

    public AuctionExpireEvent(AuctionItem auctionItem, boolean hadWinner) {
        this.auctionItem = auctionItem;
        this.hadWinner = hadWinner;
    }

    /**
     * Returns the auction item that expired.
     */
    public AuctionItem getAuctionItem() {
        return auctionItem;
    }

    /**
     * Returns true if this was a bid auction that had a winning bidder.
     * In that case, the item will be delivered to the winner.
     */
    public boolean hadWinner() {
        return hadWinner;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
