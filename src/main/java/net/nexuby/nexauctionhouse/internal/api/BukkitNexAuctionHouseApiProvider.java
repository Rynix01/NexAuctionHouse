package net.nexuby.nexauctionhouse.internal.api;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.api.NexAuctionHouseApiProvider;
import net.nexuby.nexauctionhouse.api.PlayerStats;
import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class BukkitNexAuctionHouseApiProvider implements NexAuctionHouseApiProvider {

    private final NexAuctionHouse plugin;

    public BukkitNexAuctionHouseApiProvider(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public Collection<AuctionItem> getActiveAuctions() {
        return plugin.getAuctionManager().getActiveAuctionsList();
    }

    @Override
    public List<AuctionItem> getAuctionsByPlayer(UUID playerUuid) {
        return plugin.getAuctionManager().getActiveAuctionsList().stream()
                .filter(item -> item.getSellerUuid().equals(playerUuid))
                .toList();
    }

    @Override
    public AuctionItem getAuction(int auctionId) {
        return plugin.getAuctionManager().getAuction(auctionId);
    }

    @Override
    public PlayerStats getPlayerStats(UUID playerUuid) {
        AuctionDAO dao = plugin.getAuctionManager().getDao();
        int totalSales = dao.getPlayerTotalSales(playerUuid);
        double totalRevenue = dao.getPlayerTotalRevenue(playerUuid);
        int totalPurchases = dao.getPlayerTotalPurchases(playerUuid);
        int activeListings = plugin.getAuctionManager().getPlayerActiveListings(playerUuid);
        return new PlayerStats(playerUuid, totalSales, totalRevenue, totalPurchases, activeListings);
    }

    @Override
    public double getAveragePrice(String materialName) {
        return plugin.getAuctionManager().getAveragePrice(materialName);
    }

    @Override
    public int forceCreateAuction(Player seller, ItemStack item, double price, String currency) {
        return plugin.getAuctionManager().listItem(seller, item, price, currency);
    }

    @Override
    public boolean forceRemoveAuction(int auctionId) {
        AuctionItem item = plugin.getAuctionManager().getAuction(auctionId);
        if (item == null) return false;
        return plugin.getAuctionManager().cancelAuction(null, auctionId, true);
    }

    @Override
    public boolean isBlacklisted(ItemStack item) {
        return plugin.getAuctionManager().isBlacklisted(item);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }
}
