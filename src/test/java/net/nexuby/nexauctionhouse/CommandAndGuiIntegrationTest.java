package net.nexuby.nexauctionhouse;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandAndGuiIntegrationTest extends MockPluginTestSupport {

    @Test
    void rootCommandOpensMainAuctionMenu() {
        PlayerMock player = server.addPlayer("Viewer");
        player.addAttachment(plugin, "nexauctions.use", true);

        assertTrue(server.dispatchCommand(player, "ah"));
        assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
    }

    @Test
    void sellCommandValidatesPriceAndItemBeforeTakingIt() {
        PlayerMock player = server.addPlayer("Seller");
        player.addAttachment(plugin, "nexauctions.sell", true);
        ItemStack diamond = new ItemStack(Material.DIAMOND, 2);
        player.getInventory().setItemInMainHand(diamond);

        assertTrue(server.dispatchCommand(player, "ah sell not-a-number"));
        assertEquals(Material.DIAMOND, player.getInventory().getItemInMainHand().getType());
        assertTrue(server.dispatchCommand(player, "ah sell 0"));
        assertEquals(Material.DIAMOND, player.getInventory().getItemInMainHand().getType());
        assertTrue(server.dispatchCommand(player, "ah sell 1000001"));
        assertEquals(Material.DIAMOND, player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void validSellCommandMovesItemIntoConfirmationMenu() {
        PlayerMock player = server.addPlayer("Seller");
        player.addAttachment(plugin, "nexauctions.sell", true);
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        assertTrue(server.dispatchCommand(player, "ah sell 250 money"));

        assertEquals(Material.AIR, player.getInventory().getItemInMainHand().getType());
        assertEquals(27, player.getOpenInventory().getTopInventory().getSize());
        assertEquals(Material.DIAMOND_SWORD, player.getOpenInventory().getTopInventory().getItem(13).getType());
    }

    @Test
    void blacklistAndDisabledWorldPreventListingWithoutTakingItem() {
        PlayerMock player = server.addPlayer("Seller");
        player.addAttachment(plugin, "nexauctions.sell", true);
        player.getInventory().setItemInMainHand(new ItemStack(Material.BEDROCK));
        assertTrue(server.dispatchCommand(player, "ah sell 50"));
        assertEquals(Material.BEDROCK, player.getInventory().getItemInMainHand().getType());

        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND));
        plugin.getConfigManager().getConfig().set("blacklist.disabled-worlds", List.of(player.getWorld().getName()));
        assertTrue(server.dispatchCommand(player, "ah sell 50"));
        assertEquals(Material.DIAMOND, player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void featureCommandsOpenTheirDedicatedMenus() {
        PlayerMock player = server.addPlayer("Viewer");
        player.addAttachment(plugin, "nexauctions.use", true);
        player.addAttachment(plugin, "nexauctions.bundle", true);

        assertTrue(server.dispatchCommand(player, "ah favorites"));
        assertTrue(player.getOpenInventory().getTopInventory().getSize() > 0);
        player.closeInventory();

        assertTrue(server.dispatchCommand(player, "ah history"));
        assertTrue(player.getOpenInventory().getTopInventory().getSize() > 0);
        player.closeInventory();

        assertTrue(server.dispatchCommand(player, "ah notifications"));
        assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
        player.closeInventory();

        assertTrue(server.dispatchCommand(player, "ah theme"));
        assertTrue(player.getOpenInventory().getTopInventory().getSize() > 0);
        player.closeInventory();

        assertTrue(server.dispatchCommand(player, "ah bundle 100"));
        assertTrue(player.getOpenInventory().getTopInventory().getSize() > 0);
    }

    @Test
    void consoleCannotUsePlayerOnlyCommands() {
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "ah"));
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "ah sell 100"));
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "ah expired"));
    }

    @Test
    void commandTabCompletionExposesDocumentedFeatures() {
        PlayerMock player = server.addPlayer("Viewer");
        List<String> completions = server.getPluginCommand("ah").tabComplete(player, "ah", new String[]{""});

        assertTrue(completions.contains("sell"));
        assertTrue(completions.contains("bundle"));
        assertTrue(completions.contains("favorites"));
        assertTrue(completions.contains("history"));
        assertTrue(completions.contains("notifications"));
        assertTrue(completions.contains("theme"));
    }
}
