package net.nexuby.nexauctionhouse;

import net.nexuby.nexauctionhouse.hook.item.EcoArmorHook;
import net.nexuby.nexauctionhouse.hook.item.ExcellentEnchantsHook;
import net.nexuby.nexauctionhouse.hook.item.ItemHookManager;
import net.nexuby.nexauctionhouse.hook.item.MythicCrucibleHook;
import net.nexuby.nexauctionhouse.hook.item.TalismansHook;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalDependencyIntegrationTest extends MockPluginTestSupport {

    private static final Set<String> EXPECTED_SOFT_DEPENDENCIES = Set.of(
            "Vault", "PlaceholderAPI", "PlayerPoints", "TokenManager", "CoinsEngine",
            "GemsEconomy", "EcoBits", "UltraEconomy", "HeadDatabase", "ItemsAdder",
            "Oraxen", "Nexo", "MMOItems", "MythicMobs", "MythicCrucible",
            "ExecutableItems", "EcoItems", "EcoArmor", "Talismans", "Slimefun",
            "CrazyEnchantments", "ExcellentEnchants", "ModelEngine");

    @Test
    void pluginDescriptorContainsTheCompleteOptionalDependencyMatrix() {
        assertEquals(EXPECTED_SOFT_DEPENDENCIES,
                new HashSet<>(plugin.getDescription().getSoftDepend()));
    }

    @Test
    void everyCustomItemPluginGetsItsOwnRegisteredHook() {
        Set<String> itemPlugins = Set.of(
                "HeadDatabase", "ItemsAdder", "Oraxen", "Nexo", "MMOItems",
                "MythicMobs", "MythicCrucible", "ExecutableItems", "EcoItems",
                "EcoArmor", "Talismans", "Slimefun", "CrazyEnchantments",
                "ExcellentEnchants");
        itemPlugins.forEach(MockBukkit::createMockPlugin);

        ItemHookManager manager = new ItemHookManager(plugin);
        manager.registerAll();
        Set<String> registered = new HashSet<>(manager.getHooks().stream()
                .map(hook -> hook.getPluginName())
                .toList());

        assertTrue(registered.containsAll(itemPlugins));
        assertTrue(registered.contains("CustomModelData"));
    }

    @Test
    void formerlyGroupedPluginsAreDetectedIndependently() {
        MockBukkit.createMockPlugin("MythicCrucible");
        MockBukkit.createMockPlugin("EcoArmor");
        MockBukkit.createMockPlugin("Talismans");
        MockBukkit.createMockPlugin("ExcellentEnchants");

        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                new NamespacedKey("mythiccrucible", "id"), PersistentDataType.STRING, "blade");
        meta.getPersistentDataContainer().set(
                new NamespacedKey("ecoarmor", "set"), PersistentDataType.STRING, "dragon");
        meta.getPersistentDataContainer().set(
                new NamespacedKey("talismans", "talisman"), PersistentDataType.STRING, "speed");
        meta.getPersistentDataContainer().set(
                new NamespacedKey("excellentenchants", "enchant-id"), PersistentDataType.STRING, "sharpness");
        item.setItemMeta(meta);

        MythicCrucibleHook crucible = new MythicCrucibleHook();
        EcoArmorHook armor = new EcoArmorHook();
        TalismansHook talismans = new TalismansHook();
        ExcellentEnchantsHook enchants = new ExcellentEnchantsHook();

        assertTrue(crucible.isAvailable());
        assertEquals("blade", crucible.getCustomItemId(item));
        assertTrue(armor.isAvailable());
        assertEquals("dragon", armor.getCustomItemId(item));
        assertTrue(talismans.isAvailable());
        assertEquals("speed", talismans.getCustomItemId(item));
        assertTrue(enchants.isAvailable());
        assertEquals("sharpness", enchants.getCustomItemId(item));
    }
}
