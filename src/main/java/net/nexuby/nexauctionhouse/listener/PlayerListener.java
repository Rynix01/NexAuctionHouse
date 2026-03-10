package net.nexuby.nexauctionhouse.listener;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.manager.CursorProtectionManager;
import net.nexuby.nexauctionhouse.model.ExpiredItem;
import net.nexuby.nexauctionhouse.model.PendingRevenue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

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
            CursorProtectionManager cpm = plugin.getCursorProtectionManager();

            // Load player theme preference
            plugin.getThemeManager().loadPlayerTheme(player.getUniqueId());

            boolean canReceiveLogin = plugin.getNotificationManager().canReceiveLoginNotification(player.getUniqueId());
            boolean hasSounds = plugin.getNotificationManager().hasSoundEnabled(player.getUniqueId());

            // Process rescued items (crash/disconnect protection)
            List<CursorProtectionManager.RescuedItem> rescuedItems = cpm.getRescuedItems(player.getUniqueId());
            if (!rescuedItems.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    int returned = 0;
                    int stored = 0;
                    for (CursorProtectionManager.RescuedItem rescued : rescuedItems) {
                        var remaining = player.getInventory().addItem(rescued.itemStack());
                        if (remaining.isEmpty()) {
                            returned++;
                        } else {
                            // Still can't fit - move to expired items for /ah expired
                            for (ItemStack leftover : remaining.values()) {
                                dao.insertExpiredItem(player.getUniqueId(), player.getName(), leftover, "RESCUED");
                                stored++;
                            }
                        }
                    }

                    if (canReceiveLogin) {
                        if (returned > 0) {
                            player.sendMessage(plugin.getLangManager().prefixed("auction.rescued-items",
                                    "{amount}", String.valueOf(returned)));
                        }
                        if (stored > 0) {
                            player.sendMessage(plugin.getLangManager().prefixed("auction.rescued-items-stored",
                                    "{amount}", String.valueOf(stored)));
                        }
                        if (returned > 0 && hasSounds) {
                            plugin.getNotificationManager().playRescuedSound(player);
                        }
                    }
                });

                // Delete all rescued entries async
                cpm.deleteAllRescuedItems(player.getUniqueId());
            }

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
                    if (canReceiveLogin) {
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
                        if (hasSounds) {
                            plugin.getNotificationManager().playSaleSound(player);
                        }
                    }
                });

                // Delete all processed entries async
                dao.deleteAllPendingRevenue(player.getUniqueId());
            }

            // Notify about uncollected expired items
            List<ExpiredItem> expiredItems = dao.getExpiredItems(player.getUniqueId());
            if (!expiredItems.isEmpty() && canReceiveLogin) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(plugin.getLangManager().prefixed("auction.offline-items-waiting",
                                "{amount}", String.valueOf(expiredItems.size())));
                    }
                });
            }
        }, 40L); // 2 second delay after join
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getNotificationManager().unloadSettings(event.getPlayer().getUniqueId());
        plugin.getThemeManager().unloadPlayerTheme(event.getPlayer().getUniqueId());
    }
}
