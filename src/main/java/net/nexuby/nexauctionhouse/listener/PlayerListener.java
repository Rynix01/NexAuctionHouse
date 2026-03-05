package net.nexuby.nexauctionhouse.listener;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.model.ExpiredItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public class PlayerListener implements Listener {

    private final NexAuctionHouse plugin;

    public PlayerListener(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Notify about uncollected expired items
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            AuctionDAO dao = plugin.getAuctionManager().getDao();
            List<ExpiredItem> expiredItems = dao.getExpiredItems(player.getUniqueId());

            if (!expiredItems.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(plugin.getLangManager().prefixed("auction.collected",
                                "{amount}", String.valueOf(expiredItems.size())));
                    }
                });
            }
        }, 40L); // 2 second delay after join
    }
}
