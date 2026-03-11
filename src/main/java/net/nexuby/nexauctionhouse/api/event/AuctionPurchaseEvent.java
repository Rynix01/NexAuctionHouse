package net.nexuby.nexauctionhouse.api.event;

import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a player attempts to purchase an auction item.
 * This event is cancellable — cancelling it will prevent the purchase.
 * Fired after all validation checks pass but before economy withdrawal.
 */
public class AuctionPurchaseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player buyer;
    private final AuctionItem auctionItem;
    private boolean cancelled;

    public AuctionPurchaseEvent(Player buyer, AuctionItem auctionItem) {
        this.buyer = buyer;
        this.auctionItem = auctionItem;
        this.cancelled = false;
    }

    /**
     * Returns the player attempting to buy the item.
     */
    public Player getBuyer() {
        return buyer;
    }

    /**
     * Returns the auction item being purchased.
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
