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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                    List<Integer> deliveredIds = new ArrayList<>();
                    for (CursorProtectionManager.RescuedItem rescued : rescuedItems) {
                        var remaining = player.getInventory().addItem(rescued.itemStack().clone());
                        if (remaining.isEmpty()) {
                            returned++;
                            deliveredIds.add(rescued.id());
                        } else {
                            // Still can't fit - move to expired items for /ah expired
                            boolean allStored = true;
                            for (ItemStack leftover : remaining.values()) {
                                if (dao.insertExpiredItem(player.getUniqueId(), player.getName(), leftover, "RESCUED")) {
                                    stored++;
                                } else {
                                    allStored = false;
                                }
                            }
                            if (allStored) {
                                deliveredIds.add(rescued.id());
                            }
                        }
                    }

                    if (!deliveredIds.isEmpty()) {
                        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                                () -> deliveredIds.forEach(cpm::deleteRescuedItem));
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
            }

            // Process pending revenue queue
            List<PendingRevenue> loadedRevenues = dao.getPendingRevenue(player.getUniqueId());
            long claimTime = System.currentTimeMillis();
            String revenueClaimToken = UUID.randomUUID().toString();
            List<PendingRevenue> pendingRevenues = loadedRevenues.stream()
                    .filter(revenue -> dao.claimPendingRevenue(revenue.getId(), player.getUniqueId(),
                            revenueClaimToken, claimTime, claimTime - 300_000L))
                    .toList();
            if (!pendingRevenues.isEmpty()) {
                // Group totals by currency for deposit
                Map<String, List<PendingRevenue>> revenuesByCurrency = new HashMap<>();
                for (PendingRevenue revenue : pendingRevenues) {
                    revenuesByCurrency.computeIfAbsent(revenue.getCurrency(), ignored -> new ArrayList<>())
                            .add(revenue);
                }

                // Deposit all pending money on the main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                                () -> pendingRevenues.forEach(revenue ->
                                        dao.releasePendingRevenue(revenue.getId(), revenueClaimToken)));
                        return;
                    }

                    Map<String, Double> depositedByCurrency = new HashMap<>();
                    List<PendingRevenue> depositedRevenues = new ArrayList<>();
                    for (Map.Entry<String, List<PendingRevenue>> entry : revenuesByCurrency.entrySet()) {
                        double total = entry.getValue().stream().mapToDouble(PendingRevenue::getAmount).sum();
                        if (plugin.getEconomyManager().deposit(player, total, entry.getKey())) {
                            depositedByCurrency.put(entry.getKey(), total);
                            depositedRevenues.addAll(entry.getValue());
                        }
                    }

                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        for (PendingRevenue revenue : pendingRevenues) {
                            if (depositedRevenues.contains(revenue)) {
                                dao.acknowledgePendingRevenue(revenue.getId(), revenueClaimToken);
                            } else {
                                dao.releasePendingRevenue(revenue.getId(), revenueClaimToken);
                            }
                        }
                    });

                    // Send notifications
                    if (canReceiveLogin && !depositedRevenues.isEmpty()) {
                        if (depositedRevenues.size() == 1) {
                            PendingRevenue revenue = depositedRevenues.get(0);
                            player.sendMessage(plugin.getLangManager().prefixed("auction.offline-revenue-single",
                                    "{item}", revenue.getItemName(),
                                    "{buyer}", revenue.getBuyerName(),
                                    "{amount}", plugin.getEconomyManager().format(revenue.getAmount(), revenue.getCurrency())));
                        } else {
                            // Multiple sales - send summary
                            StringBuilder details = new StringBuilder();
                            for (Map.Entry<String, Double> entry : depositedByCurrency.entrySet()) {
                                if (!details.isEmpty()) details.append(", ");
                                details.append(plugin.getEconomyManager().format(entry.getValue(), entry.getKey()));
                            }
                            player.sendMessage(plugin.getLangManager().prefixed("auction.offline-revenue-summary",
                                    "{count}", String.valueOf(depositedRevenues.size()),
                                    "{total}", details.toString()));
                        }
                        if (hasSounds) {
                            plugin.getNotificationManager().playSaleSound(player);
                        }
                    }
                });
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
