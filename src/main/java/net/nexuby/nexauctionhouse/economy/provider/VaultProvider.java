package net.nexuby.nexauctionhouse.economy.provider;

import net.milkbowl.vault.economy.Economy;
import net.nexuby.nexauctionhouse.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultProvider implements EconomyProvider {

    private Economy economy;
    private final String displayName;
    private final String currencyName;

    public VaultProvider(String displayName, String currencyName) {
        this.displayName = displayName;
        this.currencyName = currencyName;
    }

    @Override
    public String getId() {
        return "vault";
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
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return true;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}
