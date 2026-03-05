package net.nexuby.nexauctionhouse.economy.provider;

import net.nexuby.nexauctionhouse.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Economy provider that hooks into the TokenManager plugin.
 * Uses reflection to avoid hard compile-time dependency.
 */
public class TokenManagerProvider implements EconomyProvider {

    private final String displayName;
    private final String currencyName;

    private Object tokenManagerInstance;
    private Method getTokensMethod;
    private Method setTokensMethod;

    public TokenManagerProvider(String displayName, String currencyName) {
        this.displayName = displayName;
        this.currencyName = currencyName;
    }

    @Override
    public String getId() {
        return "tokenmanager";
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
        Plugin tm = Bukkit.getPluginManager().getPlugin("TokenManager");
        if (tm == null || !tm.isEnabled()) {
            return false;
        }

        try {
            // TokenManager uses static getInstance() pattern
            Class<?> tmClass = Class.forName("me.realized.tokenmanager.TokenManagerPlugin");
            Method instanceMethod = tmClass.getMethod("getInstance");
            tokenManagerInstance = instanceMethod.invoke(null);

            getTokensMethod = tmClass.getMethod("getTokens", org.bukkit.OfflinePlayer.class);
            setTokensMethod = tmClass.getMethod("setTokens", org.bukkit.OfflinePlayer.class, long.class);
            return true;
        } catch (Exception e) {
            // Fallback: try the API approach via the plugin instance
            try {
                tokenManagerInstance = tm;
                Class<?> pluginClass = tm.getClass();
                getTokensMethod = pluginClass.getMethod("getTokens", org.bukkit.OfflinePlayer.class);
                setTokensMethod = pluginClass.getMethod("setTokens", org.bukkit.OfflinePlayer.class, long.class);
                return true;
            } catch (Exception ex) {
                Bukkit.getLogger().log(Level.WARNING, "[NexAuctionHouse] Failed to hook into TokenManager API", ex);
                return false;
            }
        }
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        try {
            Object result = getTokensMethod.invoke(tokenManagerInstance, player);
            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            }
            // Some versions return OptionalLong
            return Double.parseDouble(result.toString());
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
            double current = getBalance(player);
            if (current < amount) return false;
            long newBalance = (long) (current - amount);
            setTokensMethod.invoke(tokenManagerInstance, player, newBalance);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        try {
            double current = getBalance(player);
            long newBalance = (long) (current + amount);
            setTokensMethod.invoke(tokenManagerInstance, player, newBalance);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        return String.format("%,d %s", (long) amount, displayName);
    }
}
