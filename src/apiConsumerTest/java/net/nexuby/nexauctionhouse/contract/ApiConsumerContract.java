package net.nexuby.nexauctionhouse.contract;

import net.nexuby.nexauctionhouse.api.NexAuctionHouseAPI;
import net.nexuby.nexauctionhouse.api.PlayerStats;
import net.nexuby.nexauctionhouse.api.event.AuctionCancelEvent;
import net.nexuby.nexauctionhouse.api.event.AuctionExpireEvent;
import net.nexuby.nexauctionhouse.api.event.AuctionListEvent;
import net.nexuby.nexauctionhouse.api.event.AuctionPurchaseEvent;
import net.nexuby.nexauctionhouse.api.event.BidPlaceEvent;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionStatus;
import net.nexuby.nexauctionhouse.model.AuctionType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Compiled only against the published API JAR and Paper API. */
public final class ApiConsumerContract {

    private ApiConsumerContract() {
    }

    public static void compileEveryPublicSurface(Player player, ItemStack stack, UUID playerUuid) {
        NexAuctionHouseAPI api = NexAuctionHouseAPI.getInstance();
        if (api == null) return;

        Collection<AuctionItem> active = api.getActiveAuctions();
        List<AuctionItem> owned = api.getAuctionsByPlayer(playerUuid);
        AuctionItem auction = api.getAuction(1);
        PlayerStats stats = api.getPlayerStats(playerUuid);
        double average = api.getAveragePrice("DIAMOND");
        int id = api.forceCreateAuction(player, stack, average, "money");
        boolean removed = api.forceRemoveAuction(id);
        boolean blacklisted = api.isBlacklisted(stack);
        String version = api.getVersion();

        AuctionStatus status = auction == null ? AuctionStatus.ACTIVE : auction.getStatus();
        AuctionType type = auction == null ? AuctionType.BIN : auction.getAuctionType();
        AuctionListEvent list = new AuctionListEvent(player, auction);
        AuctionPurchaseEvent purchase = new AuctionPurchaseEvent(player, auction);
        AuctionCancelEvent cancel = new AuctionCancelEvent(auction, player, false);
        AuctionExpireEvent expire = new AuctionExpireEvent(auction, false);
        BidPlaceEvent bid = new BidPlaceEvent(player, auction, average);

        consume(active, owned, stats, removed, blacklisted, version, status, type,
                list, purchase, cancel, expire, bid);
    }

    private static void consume(Object... values) {
        // Compilation is the contract; runtime behavior is covered by plugin tests.
    }
}
