package net.nexuby.nexauctionhouse.api.event;

import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a player lists an item on the auction house.
 * This event is cancellable — cancelling it will prevent the listing.
 */
public class AuctionListEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player seller;
    private final AuctionItem auctionItem;
    private boolean cancelled;

    public AuctionListEvent(Player seller, AuctionItem auctionItem) {
        this.seller = seller;
        this.auctionItem = auctionItem;
        this.cancelled = false;
    }

    /**
     * Returns the player who is listing the item.
     */
    public Player getSeller() {
        return seller;
    }

    /**
     * Returns the auction item being listed.
     * The item ID will be 0 as it has not yet been inserted into the database.
     */
    public AuctionItem getAuctionItem() {
        return auctionItem;
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
