package net.nexuby.nexauctionhouse.smoke;

import me.clip.placeholderapi.PlaceholderAPI;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

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

        UUID probeId = UUID.fromString("11111111-2222-3333-8444-555555555555");
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

        String gemsResult = "skipped";
        Plugin gemsPlugin = Bukkit.getPluginManager().getPlugin("GemsEconomy");
        if (gemsPlugin != null && gemsPlugin.isEnabled()) {
            EconomyProvider gems = nexAuctionHouse.getEconomyManager().getProviderById("gemseconomy");
            if (gems == null) {
                throw new IllegalStateException("GemsEconomy provider was not registered");
            }
            double gemsBefore = gems.getBalance(probePlayer);
            if (!gems.deposit(probePlayer, 25) || gems.getBalance(probePlayer) != gemsBefore + 25) {
                throw new IllegalStateException("GemsEconomy deposit/balance contract failed");
            }
            if (!gems.withdraw(probePlayer, 10) || gems.getBalance(probePlayer) != gemsBefore + 15) {
                throw new IllegalStateException("GemsEconomy withdraw/balance contract failed");
            }
            if (gems.format(12.5).isBlank()) {
                throw new IllegalStateException("GemsEconomy format contract failed");
            }
            gemsResult = "15";
        }

        String listings = PlaceholderAPI.setPlaceholders(probePlayer, "%nexauction_total_listings%");
        if (!listings.matches("\\d+")) {
            throw new IllegalStateException("PlaceholderAPI returned an unresolved value: " + listings);
        }

        getLogger().info("NEXAH_OPTIONAL_PROBE_PASS pointsDelta=15 coinsFormat=true gemsDelta="
                + gemsResult + " totalListings=" + listings);
    }
}
