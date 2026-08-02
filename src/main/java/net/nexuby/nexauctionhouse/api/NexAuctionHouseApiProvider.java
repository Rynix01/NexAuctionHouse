package net.nexuby.nexauctionhouse.api;

import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Internal service boundary used by the public API facade.
 *
 * <p>Third-party plugins should use {@link NexAuctionHouseAPI}; this interface is
 * public only so the runtime implementation can live outside the standalone API JAR.</p>
 */
public interface NexAuctionHouseApiProvider {

    Collection<AuctionItem> getActiveAuctions();

    List<AuctionItem> getAuctionsByPlayer(UUID playerUuid);

    AuctionItem getAuction(int auctionId);

    PlayerStats getPlayerStats(UUID playerUuid);

    double getAveragePrice(String materialName);

    int forceCreateAuction(Player seller, ItemStack item, double price, String currency);

    boolean forceRemoveAuction(int auctionId);

    boolean isBlacklisted(ItemStack item);

    String getVersion();
}
