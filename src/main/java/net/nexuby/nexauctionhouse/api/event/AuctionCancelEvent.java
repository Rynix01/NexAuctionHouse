package net.nexuby.nexauctionhouse.api.event;

import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when an auction is cancelled by the seller or an admin.
 * This event is NOT cancellable — it is informational only.
 */
public class AuctionCancelEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final AuctionItem auctionItem;
    private final Player canceller;
    private final boolean adminCancel;

    public AuctionCancelEvent(AuctionItem auctionItem, Player canceller, boolean adminCancel) {
        this.auctionItem = auctionItem;
        this.canceller = canceller;
        this.adminCancel = adminCancel;
    }

    /**
     * Returns the auction item being cancelled.
     */
    public AuctionItem getAuctionItem() {
        return auctionItem;
    }

    /**
     * Returns the player who cancelled the auction.
     * May be the seller or an admin.
     */
    public Player getCanceller() {
        return canceller;
    }

    /**
     * Returns true if the cancellation was performed by an admin.
     */
    public boolean isAdminCancel() {
        return adminCancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
