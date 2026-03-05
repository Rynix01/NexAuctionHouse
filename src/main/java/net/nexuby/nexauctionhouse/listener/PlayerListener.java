package net.nexuby.nexauctionhouse.listener;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.model.ExpiredItem;
import net.nexuby.nexauctionhouse.model.PendingRevenue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerListener implements Listener {

    private final NexAuctionHouse plugin;

    public PlayerListener(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            AuctionDAO dao = plugin.getAuctionManager().getDao();

            // Process pending revenue queue
            List<PendingRevenue> pendingRevenues = dao.getPendingRevenue(player.getUniqueId());
            if (!pendingRevenues.isEmpty()) {
                // Group totals by currency for deposit
                Map<String, Double> totalByCurrency = new HashMap<>();
                for (PendingRevenue revenue : pendingRevenues) {
                    totalByCurrency.merge(revenue.getCurrency(), revenue.getAmount(), Double::sum);
                }

                // Deposit all pending money on the main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    for (Map.Entry<String, Double> entry : totalByCurrency.entrySet()) {
                        plugin.getEconomyManager().deposit(player, entry.getValue(), entry.getKey());
                    }

                    // Send notifications
                    if (pendingRevenues.size() == 1) {
                        PendingRevenue revenue = pendingRevenues.get(0);
                        player.sendMessage(plugin.getLangManager().prefixed("auction.offline-revenue-single",
                                "{item}", revenue.getItemName(),
                                "{buyer}", revenue.getBuyerName(),
                                "{amount}", plugin.getEconomyManager().format(revenue.getAmount(), revenue.getCurrency())));
                    } else {
                        // Multiple sales - send summary
                        StringBuilder details = new StringBuilder();
                        for (Map.Entry<String, Double> entry : totalByCurrency.entrySet()) {
                            if (!details.isEmpty()) details.append(", ");
                            details.append(plugin.getEconomyManager().format(entry.getValue(), entry.getKey()));
                        }
                        player.sendMessage(plugin.getLangManager().prefixed("auction.offline-revenue-summary",
                                "{count}", String.valueOf(pendingRevenues.size()),
                                "{total}", details.toString()));
                    }
                });

                // Delete all processed entries async
                dao.deleteAllPendingRevenue(player.getUniqueId());
            }

            // Notify about uncollected expired items
            List<ExpiredItem> expiredItems = dao.getExpiredItems(player.getUniqueId());
            if (!expiredItems.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(plugin.getLangManager().prefixed("auction.offline-items-waiting",
                                "{amount}", String.valueOf(expiredItems.size())));
                    }
                });
            }
        }, 40L); // 2 second delay after join
    }
}
