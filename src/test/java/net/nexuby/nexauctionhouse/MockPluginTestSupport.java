package net.nexuby.nexauctionhouse;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class MockPluginTestSupport {

    protected ServerMock server;
    protected NexAuctionHouse plugin;
    protected Economy economy;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private Locale previousLocale;

    @BeforeEach
    void startPlugin() {
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.ROOT);
        server = MockBukkit.mock();
        PluginMock vault = MockBukkit.createMockPlugin("Vault");
        economy = mock(Economy.class);

        when(economy.format(anyDouble())).thenAnswer(invocation ->
                String.format("$%.2f", invocation.<Double>getArgument(0)));
        when(economy.getBalance(any(OfflinePlayer.class))).thenAnswer(invocation ->
                balanceOf(invocation.getArgument(0)));
        when(economy.has(any(OfflinePlayer.class), anyDouble())).thenAnswer(invocation ->
                balanceOf(invocation.getArgument(0)) >= invocation.<Double>getArgument(1));
        when(economy.withdrawPlayer(any(OfflinePlayer.class), anyDouble())).thenAnswer(invocation -> {
            OfflinePlayer player = invocation.getArgument(0);
            double amount = invocation.getArgument(1);
            double current = balanceOf(player);
            if (!Double.isFinite(amount) || amount < 0 || current < amount) {
                return response(amount, current, EconomyResponse.ResponseType.FAILURE);
            }
            double updated = current - amount;
            balances.put(player.getUniqueId(), updated);
            return response(amount, updated, EconomyResponse.ResponseType.SUCCESS);
        });
        when(economy.depositPlayer(any(OfflinePlayer.class), anyDouble())).thenAnswer(invocation -> {
            OfflinePlayer player = invocation.getArgument(0);
            double amount = invocation.getArgument(1);
            double current = balanceOf(player);
            if (!Double.isFinite(amount) || amount < 0) {
                return response(amount, current, EconomyResponse.ResponseType.FAILURE);
            }
            double updated = current + amount;
            balances.put(player.getUniqueId(), updated);
            return response(amount, updated, EconomyResponse.ResponseType.SUCCESS);
        });

        server.getServicesManager().register(Economy.class, economy, vault, ServicePriority.Normal);
        plugin = MockBukkit.load(NexAuctionHouse.class);
    }

    @AfterEach
    void stopPlugin() {
        MockBukkit.unmock();
        balances.clear();
        Locale.setDefault(previousLocale);
    }

    protected void setBalance(OfflinePlayer player, double balance) {
        balances.put(player.getUniqueId(), balance);
    }

    protected double balanceOf(OfflinePlayer player) {
        return balances.getOrDefault(player.getUniqueId(), 0.0);
    }

    private EconomyResponse response(double amount, double balance, EconomyResponse.ResponseType type) {
        return new EconomyResponse(amount, balance, type, type == EconomyResponse.ResponseType.SUCCESS ? null : "rejected");
    }
}
