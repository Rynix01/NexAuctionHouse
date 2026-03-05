package net.nexuby.nexauctionhouse.economy.provider;

import net.nexuby.nexauctionhouse.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Economy provider that hooks into UltraEconomy.
 * Supports multi-currency - the specific currency is configured via plugin-currency in config.
 */
public class UltraEconomyProvider implements EconomyProvider {

    private final String displayName;
    private final String currencyName;
    private final String pluginCurrency;

    private Object currencyObj;
    private Object apiInstance;
    private Method getAccountMethod;
    private Method getBalanceMethod;
    private Method addBalanceMethod;
    private Method removeBalanceMethod;
    private Method formatMethod;

    public UltraEconomyProvider(String displayName, String currencyName, String pluginCurrency) {
        this.displayName = displayName;
        this.currencyName = currencyName;
        this.pluginCurrency = pluginCurrency;
    }

    @Override
    public String getId() {
        return "ultraeconomy";
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
        Plugin ue = Bukkit.getPluginManager().getPlugin("UltraEconomy");
        if (ue == null || !ue.isEnabled()) {
            return false;
        }

        try {
            Class<?> ueClass = Class.forName("me.TechsCode.UltraEconomy.UltraEconomy");
            Method instanceMethod = ueClass.getMethod("getInstance");
            apiInstance = instanceMethod.invoke(null);

            // Get currency
            Method getCurrenciesMethod = apiInstance.getClass().getMethod("getCurrencies");
            Object currenciesStorage = getCurrenciesMethod.invoke(apiInstance);

            Method getByNameMethod = currenciesStorage.getClass().getMethod("name", String.class);
            Object optionalCurrency = getByNameMethod.invoke(currenciesStorage, pluginCurrency);

            // It returns an Optional-like wrapper
            Method isPresentMethod = optionalCurrency.getClass().getMethod("isPresent");
            boolean isPresent = (boolean) isPresentMethod.invoke(optionalCurrency);

            if (!isPresent) {
                Bukkit.getLogger().warning("[NexAuctionHouse] UltraEconomy currency '" + pluginCurrency + "' not found!");
                return false;
            }

            Method getMethod = optionalCurrency.getClass().getMethod("get");
            currencyObj = getMethod.invoke(optionalCurrency);

            // Get account methods
            Method getAccountsMethod = apiInstance.getClass().getMethod("getAccounts");
            Object accountsStorage = getAccountsMethod.invoke(apiInstance);

            getAccountMethod = accountsStorage.getClass().getMethod("uuid", UUID.class);

            Class<?> currencyInterface = currencyObj.getClass();
            // Account balance methods - found on account object
            // We'll resolve these dynamically at call time

            formatMethod = currencyInterface.getMethod("format", double.class);

            return true;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[NexAuctionHouse] Failed to hook into UltraEconomy API", e);
            return false;
        }
    }

    private Object getAccount(UUID uuid) {
        try {
            Object optionalAccount = getAccountMethod.invoke(
                    apiInstance.getClass().getMethod("getAccounts").invoke(apiInstance), uuid);
            Method isPresentMethod = optionalAccount.getClass().getMethod("isPresent");
            if (!(boolean) isPresentMethod.invoke(optionalAccount)) return null;
            Method getMethod = optionalAccount.getClass().getMethod("get");
            return getMethod.invoke(optionalAccount);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        try {
            Object account = getAccount(player.getUniqueId());
            if (account == null) return 0;
            Method getBalMethod = account.getClass().getMethod("getBalance", currencyObj.getClass());
            Object result = getBalMethod.invoke(account, currencyObj);
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
            Object account = getAccount(player.getUniqueId());
            if (account == null) return false;
            Method removeMethod = account.getClass().getMethod("removeBalance", currencyObj.getClass(), double.class);
            removeMethod.invoke(account, currencyObj, amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        try {
            Object account = getAccount(player.getUniqueId());
            if (account == null) return false;
            Method addMethod = account.getClass().getMethod("addBalance", currencyObj.getClass(), double.class);
            addMethod.invoke(account, currencyObj, amount);
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
