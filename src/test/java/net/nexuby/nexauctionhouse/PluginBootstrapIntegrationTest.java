package net.nexuby.nexauctionhouse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginBootstrapIntegrationTest extends MockPluginTestSupport {

    @Test
    void startsWithSqliteAndVaultAndInitializesEveryCoreService() {
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getConfigManager());
        assertNotNull(plugin.getLangManager());
        assertNotNull(plugin.getGuiConfig());
        assertNotNull(plugin.getDatabaseManager());
        assertTrue(plugin.getDatabaseManager().isUsingSQLite());
        assertNotNull(plugin.getEconomyManager().getDefaultProvider());
        assertNotNull(plugin.getItemHookManager());
        assertNotNull(plugin.getCursorProtectionManager());
        assertNotNull(plugin.getAuctionManager());
        assertNotNull(plugin.getNotificationManager());
        assertNotNull(plugin.getThemeManager());
        assertNotNull(plugin.getMigrationManager());
        assertNotNull(server.getPluginCommand("ah"));
    }
}
