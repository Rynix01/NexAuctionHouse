package net.nexuby.nexauctionhouse.smoke;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only Vault provider; never included in the production or API JAR. */
public final class VaultSmokeEconomyPlugin extends JavaPlugin {

    private final Map<String, Double> balances = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        Economy economy = (Economy) Proxy.newProxyInstance(
                Economy.class.getClassLoader(),
                new Class<?>[]{Economy.class},
                (proxy, method, args) -> invoke(proxy, method.getName(), args));
        getServer().getServicesManager().register(Economy.class, economy, this, ServicePriority.Normal);
        getLogger().info("NEXAH_VAULT_SMOKE_PROVIDER_READY");
    }

    private Object invoke(Object proxy, String method, Object[] args) {
        return switch (method) {
            case "isEnabled", "hasAccount", "createPlayerAccount" -> true;
            case "getName" -> "NexAuctionHouse Smoke Economy";
            case "hasBankSupport" -> false;
            case "fractionalDigits" -> 2;
            case "format" -> String.format("$%,.2f", amount(args));
            case "currencyNamePlural" -> "credits";
            case "currencyNameSingular" -> "credit";
            case "getBalance" -> balance(key(args));
            case "has" -> balance(key(args)) >= amount(args);
            case "withdrawPlayer" -> change(key(args), -amount(args));
            case "depositPlayer" -> change(key(args), amount(args));
            case "getBanks" -> List.of();
            case "toString" -> "NexAuctionHouse Smoke Economy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> args != null && args.length == 1 && args[0] == proxy;
            default -> failure("Bank operations are not supported by the smoke provider");
        };
    }

    private EconomyResponse change(String key, double delta) {
        double current = balance(key);
        double updated = current + delta;
        if (!Double.isFinite(delta) || updated < 0) {
            return failure("Insufficient balance or invalid amount");
        }
        balances.put(key, updated);
        return new EconomyResponse(Math.abs(delta), updated,
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse failure(String message) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, message);
    }

    private double balance(String key) {
        return balances.getOrDefault(key, 1_000_000.0);
    }

    private String key(Object[] args) {
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof OfflinePlayer player) {
                    return player.getUniqueId().toString();
                }
            }
            for (Object arg : args) {
                if (arg instanceof String value) {
                    return value;
                }
            }
        }
        return "unknown";
    }

    private double amount(Object[] args) {
        if (args != null) {
            for (int i = args.length - 1; i >= 0; i--) {
                if (args[i] instanceof Number number) {
                    return number.doubleValue();
                }
            }
        }
        return 0;
    }
}
