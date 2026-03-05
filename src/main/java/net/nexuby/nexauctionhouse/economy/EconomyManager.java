package net.nexuby.nexauctionhouse.economy;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.economy.provider.PlayerPointsProvider;
import net.nexuby.nexauctionhouse.economy.provider.TokenManagerProvider;
import net.nexuby.nexauctionhouse.economy.provider.VaultProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Multi-economy registry that manages multiple economy providers simultaneously.
 * Providers are loaded from config and any available ones are registered.
 */
public class EconomyManager {

    private final NexAuctionHouse plugin;

    // Registered providers keyed by their currency name (e.g. "money", "points", "tokens")
    private final Map<String, EconomyProvider> providersByCurrency = new LinkedHashMap<>();

    // Also keyed by provider id (e.g. "vault", "playerpoints", "tokenmanager")
    private final Map<String, EconomyProvider> providersById = new LinkedHashMap<>();

    // The default provider (first enabled one)
    private EconomyProvider defaultProvider;

    public EconomyManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    /**
     * Sets up all economy providers defined in config.
     * Returns true if at least one provider was successfully loaded.
     */
    public boolean setup() {
        providersByCurrency.clear();
        providersById.clear();
        defaultProvider = null;

        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection economySection = config.getConfigurationSection("economy.providers");

        if (economySection == null) {
            // Fallback: try to load Vault with default settings
            VaultProvider vault = new VaultProvider("Money", "money");
            if (vault.isAvailable()) {
                registerProvider(vault);
                return true;
            }
            return false;
        }

        for (String key : economySection.getKeys(false)) {
            ConfigurationSection providerSection = economySection.getConfigurationSection(key);
            if (providerSection == null) continue;

            boolean enabled = providerSection.getBoolean("enabled", false);
            if (!enabled) continue;

            String displayName = providerSection.getString("display-name", key);
            String currencyName = providerSection.getString("currency-name", key).toLowerCase();

            EconomyProvider provider = createProvider(key, displayName, currencyName);
            if (provider == null) {
                plugin.getLogger().warning("Unknown economy provider type: " + key);
                continue;
            }

            if (provider.isAvailable()) {
                registerProvider(provider);
                plugin.getLogger().info("Economy provider registered: " + provider.getDisplayName()
                        + " (currency: " + provider.getCurrencyName() + ")");
            } else {
                plugin.getLogger().warning("Economy provider '" + key + "' is enabled but the plugin is not available.");
            }
        }

        return defaultProvider != null;
    }

    private EconomyProvider createProvider(String type, String displayName, String currencyName) {
        return switch (type.toLowerCase()) {
            case "vault" -> new VaultProvider(displayName, currencyName);
            case "playerpoints" -> new PlayerPointsProvider(displayName, currencyName);
            case "tokenmanager" -> new TokenManagerProvider(displayName, currencyName);
            default -> null;
        };
    }

    private void registerProvider(EconomyProvider provider) {
        providersByCurrency.put(provider.getCurrencyName(), provider);
        providersById.put(provider.getId(), provider);
        if (defaultProvider == null) {
            defaultProvider = provider;
        }
    }

    // -- Provider lookup --

    public EconomyProvider getProvider(String currency) {
        if (currency == null || currency.isEmpty()) {
            return defaultProvider;
        }
        EconomyProvider provider = providersByCurrency.get(currency.toLowerCase());
        return provider != null ? provider : defaultProvider;
    }

    public EconomyProvider getProviderById(String id) {
        return providersById.get(id);
    }

    public EconomyProvider getDefaultProvider() {
        return defaultProvider;
    }

    public Collection<EconomyProvider> getProviders() {
        return Collections.unmodifiableCollection(providersById.values());
    }

    public List<String> getCurrencyNames() {
        return new ArrayList<>(providersByCurrency.keySet());
    }

    public boolean isValidCurrency(String currency) {
        return providersByCurrency.containsKey(currency.toLowerCase());
    }

    // -- Convenience methods that use currency parameter --

    public double getBalance(OfflinePlayer player, String currency) {
        return getProvider(currency).getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount, String currency) {
        return getProvider(currency).has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount, String currency) {
        return getProvider(currency).withdraw(player, amount);
    }

    public boolean deposit(OfflinePlayer player, double amount, String currency) {
        return getProvider(currency).deposit(player, amount);
    }

    public String format(double amount, String currency) {
        return getProvider(currency).format(amount);
    }

    // -- Legacy convenience methods using default provider --

    public double getBalance(OfflinePlayer player) {
        return defaultProvider.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return defaultProvider.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        return defaultProvider.withdraw(player, amount);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return defaultProvider.deposit(player, amount);
    }

    public String format(double amount) {
        return defaultProvider.format(amount);
    }
}
