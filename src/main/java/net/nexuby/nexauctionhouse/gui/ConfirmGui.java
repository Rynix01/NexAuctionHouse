package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Simple confirmation dialog before purchasing an item.
 * Not paginated - just a small 27-slot menu with confirm/cancel buttons.
 */
public class ConfirmGui extends AbstractGui {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final AuctionItem auctionItem;

    private int confirmSlot = -1;
    private int cancelSlot = -1;
    private int displaySlot = -1;

    public ConfirmGui(NexAuctionHouse plugin, Player viewer, AuctionItem auctionItem) {
        super(plugin, viewer);
        this.auctionItem = auctionItem;
    }

    @Override
    protected void build() {
        FileConfiguration cfg = plugin.getGuiConfig().getGui("confirm");
        if (cfg == null) {
            plugin.getLogger().warning("GUI config 'confirm' not found!");
            return;
        }

        String rawTitle = cfg.getString("title", "<dark_gray>Confirm Purchase");
        String title = rawTitle
                .replace("{item}", net.nexuby.nexauctionhouse.manager.AuctionManager.getItemName(auctionItem.getItemStack()))
                .replace("{price}", plugin.getEconomyManager().format(auctionItem.getPrice()));

        int size = cfg.getInt("size", 27);
        inventory = Bukkit.createInventory(this, size, mm.deserialize(title));

        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons != null) {
            // Confirm button
            if (buttons.contains("confirm")) {
                confirmSlot = buttons.getInt("confirm.slot", 11);
                ItemStack confirmItem = createConfirmButton(buttons.getConfigurationSection("confirm"));
                inventory.setItem(confirmSlot, confirmItem);
            }

            // Cancel button
            if (buttons.contains("cancel")) {
                cancelSlot = buttons.getInt("cancel.slot", 15);
                inventory.setItem(cancelSlot, createButton(buttons.getConfigurationSection("cancel")));
            }

            // Display the auction item itself
            if (buttons.contains("display-item")) {
                displaySlot = buttons.getInt("display-item.slot", 13);
                inventory.setItem(displaySlot, auctionItem.getItemStack().clone());
            }
        }

        // Fill remaining slots
        applyFiller(cfg);
    }

    private ItemStack createConfirmButton(ConfigurationSection section) {
        ItemStack button = createButton(section);

        // Replace placeholders in the lore
        if (button.hasItemMeta()) {
            var meta = button.getItemMeta();
            if (meta.hasLore()) {
                var newLore = new java.util.ArrayList<net.kyori.adventure.text.Component>();
                String itemName = net.nexuby.nexauctionhouse.manager.AuctionManager.getItemName(auctionItem.getItemStack());
                String priceStr = plugin.getEconomyManager().format(auctionItem.getPrice());

                for (var loreLine : meta.lore()) {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(loreLine);
                    // Re-parse with replacements using the raw config values
                    newLore.add(loreLine);
                }
                // Rebuild from config with replacements
                if (section.contains("lore")) {
                    newLore.clear();
                    for (String line : section.getStringList("lore")) {
                        String parsed = line
                                .replace("{item}", itemName)
                                .replace("{price}", priceStr);
                        newLore.add(mm.deserialize(parsed));
                    }
                }
                meta.lore(newLore);
                button.setItemMeta(meta);
            }
        }

        return button;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!checkCooldown((Player) event.getWhoClicked())) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        if (slot == confirmSlot) {
            handlePurchase();
        } else if (slot == cancelSlot) {
            // Go back to main menu
            new MainMenu(plugin, viewer).open();
        }
    }

    private void handlePurchase() {
        // Double-check the auction still exists
        AuctionItem current = plugin.getAuctionManager().getAuction(auctionItem.getId());
        if (current == null) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.auction-not-found"));
            viewer.closeInventory();
            return;
        }

        // Can't buy your own item
        if (current.getSellerUuid().equals(viewer.getUniqueId())) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.cannot-buy-own"));
            viewer.closeInventory();
            return;
        }

        // Check balance
        if (!plugin.getEconomyManager().has(viewer, current.getPrice())) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.not-enough-money",
                    "{price}", plugin.getEconomyManager().format(current.getPrice())));
            viewer.closeInventory();
            return;
        }

        // Check inventory space
        if (viewer.getInventory().firstEmpty() == -1) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.inventory-full"));
            viewer.closeInventory();
            return;
        }

        // Attempt purchase - this is atomic in AuctionManager (ConcurrentHashMap.remove)
        boolean success = plugin.getAuctionManager().purchaseItem(viewer, auctionItem.getId());

        if (success) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.purchased",
                    "{item}", net.nexuby.nexauctionhouse.manager.AuctionManager.getItemName(current.getItemStack()),
                    "{price}", plugin.getEconomyManager().format(current.getPrice())));
        } else {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.auction-not-found"));
        }

        viewer.closeInventory();
    }
}
