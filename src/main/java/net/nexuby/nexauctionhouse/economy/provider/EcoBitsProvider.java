package net.nexuby.nexauctionhouse.economy.provider;

import net.nexuby.nexauctionhouse.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.logging.Level;

/**
 * Economy provider that hooks into EcoBits (Auxilor).
 * Supports multi-currency - the specific currency is configured via plugin-currency in config.
 */
public class EcoBitsProvider implements EconomyProvider {

    private final String displayName;
    private final String currencyName;
    private final String pluginCurrency;

    private Object currencyObj;
    private Method getBalanceMethod;
    private Method adjustBalanceMethod;

    public EcoBitsProvider(String displayName, String currencyName, String pluginCurrency) {
        this.displayName = displayName;
        this.currencyName = currencyName;
        this.pluginCurrency = pluginCurrency;
    }

    @Override
    public String getId() {
        return "ecobits";
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
        Plugin eb = Bukkit.getPluginManager().getPlugin("EcoBits");
        if (eb == null || !eb.isEnabled()) {
            return false;
        }

        try {
            // Get the currency object
            Class<?> currenciesClass = Class.forName("com.willfp.ecobits.currencies.Currencies");
            Method getByIdMethod = currenciesClass.getMethod("getByID", String.class);
            currencyObj = getByIdMethod.invoke(null, pluginCurrency);

            if (currencyObj == null) {
                Bukkit.getLogger().warning("[NexAuctionHouse] EcoBits currency '" + pluginCurrency + "' not found!");
                return false;
            }

            Class<?> currencyUtilsClass = Class.forName("com.willfp.ecobits.currencies.CurrencyUtils");
            Class<?> currencyClass = Class.forName("com.willfp.ecobits.currencies.Currency");

            getBalanceMethod = currencyUtilsClass.getMethod("getBalance", org.bukkit.OfflinePlayer.class, currencyClass);
            adjustBalanceMethod = currencyUtilsClass.getMethod("adjustBalance", org.bukkit.OfflinePlayer.class, currencyClass, BigDecimal.class);

            return true;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[NexAuctionHouse] Failed to hook into EcoBits API", e);
            return false;
        }
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        try {
            Object result = getBalanceMethod.invoke(null, player, currencyObj);
            if (result instanceof BigDecimal bd) {
                return bd.doubleValue();
            }
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
            BigDecimal negative = BigDecimal.valueOf(-amount);
            adjustBalanceMethod.invoke(null, player, currencyObj, negative);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        try {
            BigDecimal positive = BigDecimal.valueOf(amount);
            adjustBalanceMethod.invoke(null, player, currencyObj, positive);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        return String.format("%,.2f %s", amount, displayName);
    }
}
