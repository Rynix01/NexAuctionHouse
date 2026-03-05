package net.nexuby.nexauctionhouse.economy.provider;

import net.nexuby.nexauctionhouse.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Economy provider that hooks into the PlayerPoints plugin.
 * Uses reflection to avoid hard compile-time dependency.
 */
public class PlayerPointsProvider implements EconomyProvider {

    private final String displayName;
    private final String currencyName;

    private Object apiInstance;
    private Method lookMethod;
    private Method giveMethod;
    private Method takeMethod;

    public PlayerPointsProvider(String displayName, String currencyName) {
        this.displayName = displayName;
        this.currencyName = currencyName;
    }

    @Override
    public String getId() {
        return "playerpoints";
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getCurrencyName() {
        return currencyName;
    }

    @Override
    public boolean isAvailable() {
        Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (pp == null || !pp.isEnabled()) {
            return false;
        }

        try {
            Method getApiMethod = pp.getClass().getMethod("getAPI");
            apiInstance = getApiMethod.invoke(pp);
            Class<?> apiClass = apiInstance.getClass();

            lookMethod = apiClass.getMethod("look", UUID.class);
            giveMethod = apiClass.getMethod("give", UUID.class, int.class);
            takeMethod = apiClass.getMethod("take", UUID.class, int.class);
            return true;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[NexAuctionHouse] Failed to hook into PlayerPoints API", e);
            return false;
        }
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        try {
            return (int) lookMethod.invoke(apiInstance, player.getUniqueId());
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        try {
            return (boolean) takeMethod.invoke(apiInstance, player.getUniqueId(), (int) amount);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        try {
            return (boolean) giveMethod.invoke(apiInstance, player.getUniqueId(), (int) amount);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        return String.format("%,d %s", (int) amount, displayName);
    }
}
