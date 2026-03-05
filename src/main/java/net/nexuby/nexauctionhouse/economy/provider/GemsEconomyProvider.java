package net.nexuby.nexauctionhouse.economy.provider;

import net.nexuby.nexauctionhouse.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Economy provider that hooks into GemsEconomy.
 * Supports multi-currency - the specific currency is configured via plugin-currency in config.
 */
public class GemsEconomyProvider implements EconomyProvider {

    private final String displayName;
    private final String currencyName;
    private final String pluginCurrency;

    private Object currencyObj;
    private Object apiInstance;
    private Method depositMethod;
    private Method withdrawMethod;
    private Method balanceMethod;
    private Method formatMethod;

    public GemsEconomyProvider(String displayName, String currencyName, String pluginCurrency) {
        this.displayName = displayName;
        this.currencyName = currencyName;
        this.pluginCurrency = pluginCurrency;
    }

    @Override
    public String getId() {
        return "gemseconomy";
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
        Plugin ge = Bukkit.getPluginManager().getPlugin("GemsEconomy");
        if (ge == null || !ge.isEnabled()) {
            return false;
        }

        try {
            // GemsEconomy API access
            Class<?> apiClass = Class.forName("me.xanium.gemseconomy.api.GemsEconomyAPI");
            apiInstance = apiClass.getDeclaredConstructor().newInstance();

            // Get currency
            Class<?> geClass = Class.forName("me.xanium.gemseconomy.GemsEconomy");
            Method instanceMethod = geClass.getMethod("getInstance");
            Object geInstance = instanceMethod.invoke(null);

            Method getCurrencyManagerMethod = geClass.getMethod("getCurrencyManager");
            Object currencyManager = getCurrencyManagerMethod.invoke(geInstance);

            Method getCurrencyMethod = currencyManager.getClass().getMethod("getCurrency", String.class);
            currencyObj = getCurrencyMethod.invoke(currencyManager, pluginCurrency);

            if (currencyObj == null) {
                Bukkit.getLogger().warning("[NexAuctionHouse] GemsEconomy currency '" + pluginCurrency + "' not found!");
                return false;
            }

            Class<?> currencyClass = Class.forName("me.xanium.gemseconomy.economy.Currency");

            depositMethod = apiClass.getMethod("deposit", UUID.class, double.class, currencyClass);
            withdrawMethod = apiClass.getMethod("withdraw", UUID.class, double.class, currencyClass);
            balanceMethod = apiClass.getMethod("getBalance", UUID.class, currencyClass);
            formatMethod = currencyClass.getMethod("format", double.class);

            return true;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[NexAuctionHouse] Failed to hook into GemsEconomy API", e);
            return false;
        }
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        try {
            Object result = balanceMethod.invoke(apiInstance, player.getUniqueId(), currencyObj);
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
        try {
            withdrawMethod.invoke(apiInstance, player.getUniqueId(), amount, currencyObj);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        try {
            depositMethod.invoke(apiInstance, player.getUniqueId(), amount, currencyObj);
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
