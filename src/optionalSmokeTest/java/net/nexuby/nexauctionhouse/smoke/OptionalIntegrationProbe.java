package net.nexuby.nexauctionhouse.smoke;

import me.clip.placeholderapi.PlaceholderAPI;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Runs inside the disposable Paper harness; never included in the production JAR. */
public final class OptionalIntegrationProbe extends JavaPlugin {

    @Override
    public void onEnable() {
        Plugin loaded = Bukkit.getPluginManager().getPlugin("NexAuctionHouse");
        if (!(loaded instanceof NexAuctionHouse nexAuctionHouse)) {
            throw new IllegalStateException("NexAuctionHouse runtime is unavailable");
        }

        EconomyProvider points = nexAuctionHouse.getEconomyManager().getProviderById("playerpoints");
        if (points == null) {
            throw new IllegalStateException("PlayerPoints provider was not registered");
        }

        UUID probeId = UUID.nameUUIDFromBytes(
                "nexauctionhouse-optional-smoke".getBytes(StandardCharsets.UTF_8));
        OfflinePlayer probePlayer = Bukkit.getOfflinePlayer(probeId);
        double before = points.getBalance(probePlayer);
        if (!points.deposit(probePlayer, 25) || points.getBalance(probePlayer) != before + 25) {
            throw new IllegalStateException("PlayerPoints deposit/balance contract failed");
        }
        if (!points.withdraw(probePlayer, 10) || points.getBalance(probePlayer) != before + 15) {
            throw new IllegalStateException("PlayerPoints withdraw/balance contract failed");
        }

        EconomyProvider coins = nexAuctionHouse.getEconomyManager().getProviderById("coinsengine");
        if (coins == null || coins.format(12.5).isBlank()) {
            throw new IllegalStateException("CoinsEngine provider/API contract failed");
        }

        String listings = PlaceholderAPI.setPlaceholders(probePlayer, "%nexauction_total_listings%");
        if (!listings.matches("\\d+")) {
            throw new IllegalStateException("PlaceholderAPI returned an unresolved value: " + listings);
        }

        getLogger().info("NEXAH_OPTIONAL_PROBE_PASS pointsDelta=15 coinsFormat=true totalListings=" + listings);
    }
}
