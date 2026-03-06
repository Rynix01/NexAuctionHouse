package net.nexuby.nexauctionhouse.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for NexAuctionHouse.
 *
 * Available placeholders:
 *   %nexauction_total_listings%      - Total active listings on the server
 *   %nexauction_player_listings%     - Player's active listing count
 *   %nexauction_player_limit%        - Player's listing limit
 *   %nexauction_player_expired%      - Player's uncollected expired items count
 *   %nexauction_player_tax%          - Player's tax rate
 *   %nexauction_player_total_sales%  - Player's total completed sale count
 *   %nexauction_player_total_revenue% - Player's total revenue earned
 *   %nexauction_player_total_purchases% - Player's total purchase count
 *   %nexauction_avg_price_<MATERIAL>% - Average price for a material (last 7 days)
 */
public class AuctionPlaceholders extends PlaceholderExpansion {

    private final NexAuctionHouse plugin;

    public AuctionPlaceholders(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nexauction";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Rynix";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        AuctionManager manager = plugin.getAuctionManager();

        // Server-wide placeholders
        if (params.equalsIgnoreCase("total_listings")) {
            return String.valueOf(manager.getActiveAuctionsList().size());
        }

        // Average price by material: avg_price_DIAMOND_SWORD
        if (params.toLowerCase().startsWith("avg_price_")) {
            String material = params.substring("avg_price_".length()).toUpperCase();
            double avg = manager.getAveragePrice(material);
            return avg > 0 ? plugin.getEconomyManager().format(avg) : "0";
        }

        // Player-specific placeholders require a valid player
        if (player == null) return null;

        switch (params.toLowerCase()) {
            case "player_listings":
                return String.valueOf(manager.getPlayerActiveListings(player.getUniqueId()));

            case "player_limit":
                if (player.isOnline()) {
                    return String.valueOf(manager.getPlayerListingLimit(player.getPlayer()));
                }
                return String.valueOf(plugin.getConfigManager().getDefaultListingLimit());

            case "player_expired":
                return String.valueOf(
                        manager.getDao().getExpiredItems(player.getUniqueId()).size());

            case "player_tax":
                if (player.isOnline()) {
                    return String.format("%.1f%%", manager.getPlayerTaxRate(player.getPlayer()));
                }
                return String.format("%.1f%%", plugin.getConfigManager().getDefaultTaxRate());

            case "player_total_sales":
                return String.valueOf(manager.getDao().getPlayerTotalSales(player.getUniqueId()));

            case "player_total_revenue":
                return plugin.getEconomyManager().format(
                        manager.getDao().getPlayerTotalRevenue(player.getUniqueId()));

            case "player_total_purchases":
                return String.valueOf(manager.getDao().getPlayerTotalPurchases(player.getUniqueId()));

            default:
                return null;
        }
    }
}
