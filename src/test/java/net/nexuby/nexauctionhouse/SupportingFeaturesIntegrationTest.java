package net.nexuby.nexauctionhouse;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.config.ConfigManager;
import net.nexuby.nexauctionhouse.manager.CursorProtectionManager;
import net.nexuby.nexauctionhouse.model.NotificationSettings;
import net.nexuby.nexauctionhouse.util.ItemSerializer;
import net.nexuby.nexauctionhouse.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SupportingFeaturesIntegrationTest extends MockPluginTestSupport {

    @Test
    void defaultConfigurationExposesEveryFeatureGroup() {
        ConfigManager config = plugin.getConfigManager();
        assertEquals("sqlite", config.getDatabaseType());
        assertEquals(1, config.getMinPrice(), 0.001);
        assertEquals(1_000_000, config.getMaxPrice(), 0.001);
        assertTrue(config.isTaxEnabled());
        assertTrue(config.isBidEnabled());
        assertTrue(config.isAutoRelistEnabled());
        assertTrue(config.isBundleEnabled());
        assertFalse(config.isCrossServerEnabled());
        assertFalse(config.isDiscordEnabled());
        assertEquals(50, config.getMaxFavorites());
        assertEquals(5, config.getDefaultListingLimit());
        assertEquals(72, config.getMaxAuctionDuration());
    }

    @Test
    void notificationDefaultsPersistAndReload() {
        UUID player = UUID.randomUUID();
        NotificationSettings settings = plugin.getNotificationManager().getSettings(player);
        assertTrue(settings.isSaleNotifications());
        assertTrue(settings.isBidNotifications());
        assertTrue(settings.isSoundEffects());

        settings.setSaleNotifications(false);
        settings.setSoundEffects(false);
        plugin.getNotificationManager().saveSettings(settings);
        plugin.getNotificationManager().unloadSettings(player);

        NotificationSettings reloaded = plugin.getNotificationManager().getSettings(player);
        assertFalse(reloaded.isSaleNotifications());
        assertFalse(reloaded.isSoundEffects());
        assertTrue(reloaded.isBidNotifications());
    }

    @Test
    void themesLoadAndPlayerSelectionPersists() {
        Set<String> themeIds = plugin.getThemeManager().getThemeIds();
        assertTrue(themeIds.containsAll(Set.of("default", "dark", "nether")));
        UUID player = UUID.randomUUID();

        plugin.getThemeManager().savePlayerTheme(player, "dark");
        plugin.getThemeManager().unloadPlayerTheme(player);
        plugin.getThemeManager().loadPlayerTheme(player);

        assertEquals("dark", plugin.getThemeManager().getPlayerTheme(player));
        assertNotNull(plugin.getThemeManager().getFillerMaterial(player));
        assertFalse(plugin.getThemeManager().getTitleColor(player).isBlank());
    }

    @Test
    void cursorProtectionTracksClearsAndAtomicallyClaimsRescuedItems() {
        CursorProtectionManager manager = plugin.getCursorProtectionManager();
        UUID player = UUID.randomUUID();
        manager.trackCursorItem(player, "Player", new ItemStack(Material.DIAMOND));
        assertEquals(1, manager.getRescuedItems(player).size());
        manager.clearTracked(player);
        assertTrue(manager.getRescuedItems(player).isEmpty());

        manager.saveRescuedItem(player, "Player", new ItemStack(Material.EMERALD, 2), "DISCONNECT");
        CursorProtectionManager.RescuedItem rescued = manager.getRescuedItems(player).getFirst();
        long now = System.currentTimeMillis();
        assertFalse(manager.claimRescuedItem(rescued.id(), UUID.randomUUID(), "bad", now, now - 60_000));
        assertTrue(manager.claimRescuedItem(rescued.id(), player, "token", now, now - 60_000));
        assertFalse(manager.acknowledgeRescuedItem(rescued.id(), "wrong"));
        assertTrue(manager.acknowledgeRescuedItem(rescued.id(), "token"));
        assertTrue(manager.getRescuedItems(player).isEmpty());
    }

    @Test
    void itemSerializationRoundTripsMetadataAndBundles() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.displayName(Component.text("Audit Blade"));
        meta.lore(List.of(Component.text("Line one"), Component.text("Line two")));
        sword.setItemMeta(meta);

        ItemStack restored = ItemSerializer.fromBase64(ItemSerializer.toBase64(sword));
        assertNotNull(restored);
        assertEquals(sword.getType(), restored.getType());
        assertEquals(sword.getItemMeta().displayName(), restored.getItemMeta().displayName());
        assertEquals(sword.getItemMeta().lore(), restored.getItemMeta().lore());

        List<ItemStack> bundle = List.of(sword, new ItemStack(Material.EMERALD, 5));
        List<ItemStack> restoredBundle = ItemSerializer.bundleFromBase64(ItemSerializer.bundleToBase64(bundle));
        assertEquals(2, restoredBundle.size());
        assertEquals(Material.EMERALD, restoredBundle.get(1).getType());
        assertEquals(5, restoredBundle.get(1).getAmount());
    }

    @Test
    void timeFormattingUsesLanguageResources() {
        assertEquals("Expired", TimeUtil.formatDuration(0));
        assertEquals("1h 5m", TimeUtil.formatDuration(3_900_000));
        assertEquals("0m", TimeUtil.formatDuration(30_000));
    }

    @Test
    void migrationRegistryContainsEverySupportedSource() {
        assertEquals(Set.of("auctionhouse", "crazyauctions", "zauctionhouse", "auctionmaster"),
                plugin.getMigrationManager().getSupportedPlugins());
        assertNotNull(plugin.getMigrationManager().getMigrator("CrazyAuctions"));
        assertFalse(plugin.getMigrationManager().isMigrationInProgress());
    }
}
