package net.nexuby.nexauctionhouse.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Abstraction for different economy backends (Vault, PlayerPoints, TokenManager, etc.)
 */
public interface EconomyProvider {

    /**
     * Internal identifier used in config and database (e.g. "vault", "playerpoints", "tokenmanager").
     */
    String getId();

    /**
     * Display name shown to players in GUIs and messages (e.g. "Money", "Points", "Tokens").
     */
    String getDisplayName();

    /**
     * The currency name used in commands (e.g. "money", "points", "tokens").
     */
    String getCurrencyName();

    /**
     * Checks if the backing plugin is present and the economy is usable.
     */
    boolean isAvailable();

    /**
     * Returns the player's current balance in this economy.
     */
    double getBalance(OfflinePlayer player);

    /**
     * Checks whether the player has at least the given amount.
     */
    boolean has(OfflinePlayer player, double amount);

    /**
     * Withdraws an amount from the player's balance.
     * Returns true on success.
     */
    boolean withdraw(OfflinePlayer player, double amount);

    /**
     * Deposits an amount to the player's balance.
     * Returns true on success.
     */
    boolean deposit(OfflinePlayer player, double amount);

    /**
     * Formats the given amount into a human-readable string (e.g. "$1,000" or "500 Points").
     */
    String format(double amount);
}
