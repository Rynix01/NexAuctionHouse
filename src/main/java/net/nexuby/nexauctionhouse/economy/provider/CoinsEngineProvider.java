package net.nexuby.nexauctionhouse.economy.provider;

import net.nexuby.nexauctionhouse.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Economy provider that hooks into CoinsEngine (NightExpress).
 * Supports multi-currency - the specific currency is configured via plugin-currency in config.
 */
public class CoinsEngineProvider implements EconomyProvider {

    private final String displayName;
    private final String currencyName;
    private final String pluginCurrency;

    private Object currencyObj;
    private Method getBalanceMethod;
    private Method addBalanceMethod;
    private Method removeBalanceMethod;
    private Method formatMethod;

    public CoinsEngineProvider(String displayName, String currencyName, String pluginCurrency) {
        this.displayName = displayName;
        this.currencyName = currencyName;
        this.pluginCurrency = pluginCurrency;
    }

    @Override
    public String getId() {
        return "coinsengine";
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
        Plugin ce = Bukkit.getPluginManager().getPlugin("CoinsEngine");
        if (ce == null || !ce.isEnabled()) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            Method getCurrencyMethod = apiClass.getMethod("getCurrency", String.class);
            currencyObj = getCurrencyMethod.invoke(null, pluginCurrency);

            if (currencyObj == null) {
                Bukkit.getLogger().warning("[NexAuctionHouse] CoinsEngine currency '" + pluginCurrency + "' not found!");
                return false;
            }

            Class<?> currencyClass = currencyObj.getClass();
            // Find the Currency interface/superclass
            Class<?> currencyInterface = Class.forName("su.nightexpress.coinsengine.api.currency.Currency");

            getBalanceMethod = apiClass.getMethod("getBalance", org.bukkit.entity.Player.class, currencyInterface);
            addBalanceMethod = apiClass.getMethod("addBalance", org.bukkit.entity.Player.class, currencyInterface, double.class);
            removeBalanceMethod = apiClass.getMethod("removeBalance", org.bukkit.entity.Player.class, currencyInterface, double.class);
            formatMethod = currencyClass.getMethod("format", double.class);

            return true;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[NexAuctionHouse] Failed to hook into CoinsEngine API", e);
            return false;
        }
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null || !player.isOnline()) return 0;
        try {
            Object result = getBalanceMethod.invoke(null, player.getPlayer(), currencyObj);
            return ((Number) result).doubleValue();
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
        if (player == null || !player.isOnline()) return false;
        try {
            removeBalanceMethod.invoke(null, player.getPlayer(), currencyObj, amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (player == null || !player.isOnline()) return false;
        try {
            addBalanceMethod.invoke(null, player.getPlayer(), currencyObj, amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        try {
            Object result = formatMethod.invoke(currencyObj, amount);
            return result.toString();
        } catch (Exception e) {
            return String.format("%,.2f %s", amount, displayName);
        }
    }
}
