package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.listener.ChatInputListener;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirmation / bid dialog for auction-type (bid) listings.
 * Shows item info, current bid, and a button to place a bid via chat input.
 */
public class BidGui extends AbstractGui {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final AuctionItem auctionItem;

    private int bidSlot = -1;
    private int cancelSlot = -1;
    private int displaySlot = -1;

    public BidGui(NexAuctionHouse plugin, Player viewer, AuctionItem auctionItem) {
        super(plugin, viewer);
        this.auctionItem = auctionItem;
    }

    @Override
    protected void build() {
        FileConfiguration cfg = plugin.getGuiConfig().getGui("bid");
        if (cfg == null) {
            plugin.getLogger().warning("GUI config 'bid' not found!");
            return;
        }

        String itemName = AuctionManager.getItemName(auctionItem.getItemStack());
        double minBid = plugin.getAuctionManager().getMinBid(auctionItem);
        String minBidStr = plugin.getEconomyManager().format(minBid, auctionItem.getCurrency());
        String currentBidStr = auctionItem.getHighestBid() > 0
                ? plugin.getEconomyManager().format(auctionItem.getHighestBid(), auctionItem.getCurrency())
                : plugin.getLangManager().getRaw("bid.no-bids-yet");
        String bidderName = auctionItem.getHighestBidderName() != null
                ? auctionItem.getHighestBidderName() : "-";

        String rawTitle = cfg.getString("title", "<dark_gray>Place Bid");
        String title = rawTitle.replace("{item}", itemName);

        int size = cfg.getInt("size", 27);
        inventory = Bukkit.createInventory(this, size, text(title));

        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons != null) {
            if (buttons.contains("bid")) {
                bidSlot = buttons.getInt("bid.slot", 11);
                ItemStack bidButton = createBidButton(buttons.getConfigurationSection("bid"),
                        itemName, currentBidStr, minBidStr, bidderName);
                inventory.setItem(bidSlot, bidButton);
            }

            if (buttons.contains("cancel")) {
                cancelSlot = buttons.getInt("cancel.slot", 15);
                inventory.setItem(cancelSlot, createButton(buttons.getConfigurationSection("cancel")));
            }

            if (buttons.contains("display-item")) {
                displaySlot = buttons.getInt("display-item.slot", 13);
                inventory.setItem(displaySlot, auctionItem.getItemStack().clone());
            }
        }

        applyFiller(cfg);
    }

    private ItemStack createBidButton(ConfigurationSection section, String itemName,
                                       String currentBid, String minBid, String bidderName) {
        ItemStack button = createButton(section);

        if (section.contains("lore")) {
            var meta = button.getItemMeta();
            List<net.kyori.adventure.text.Component> newLore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                String parsed = line
                        .replace("{item}", itemName)
                        .replace("{current_bid}", currentBid)
                        .replace("{min_bid}", minBid)
                        .replace("{bidder}", bidderName)
                        .replace("{seller}", auctionItem.getSellerName());
                newLore.add(text(parsed));
            }
            meta.lore(newLore);
            button.setItemMeta(meta);
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

        if (slot == bidSlot) {
            handleBidInput();
        } else if (slot == cancelSlot) {
            new MainMenu(plugin, viewer).open();
        }
    }

    private void handleBidInput() {
        double minBid = plugin.getAuctionManager().getMinBid(auctionItem);
        String minBidStr = plugin.getEconomyManager().format(minBid, auctionItem.getCurrency());

        viewer.closeInventory();
        viewer.sendMessage(plugin.getLangManager().prefixed("bid.enter-bid-amount",
                "{min}", minBidStr));
        viewer.sendMessage(plugin.getLangManager().prefixed("auction.type-cancel"));

        ChatInputListener.awaitInput(viewer, input -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (input.equalsIgnoreCase("cancel")) {
                    viewer.sendMessage(plugin.getLangManager().prefixed("bid.bid-cancelled"));
                    new MainMenu(plugin, viewer).open();
                    return;
                }

                double amount;
                try {
                    amount = Double.parseDouble(input);
                } catch (NumberFormatException e) {
                    viewer.sendMessage(plugin.getLangManager().prefixed("auction.invalid-price"));
                    new MainMenu(plugin, viewer).open();
                    return;
                }

                // Re-check the auction state
                AuctionItem current = plugin.getAuctionManager().getAuction(auctionItem.getId());
                if (current == null || current.isExpired()) {
                    viewer.sendMessage(plugin.getLangManager().prefixed("auction.auction-not-found"));
                    return;
                }

                // Check if already highest bidder
                if (viewer.getUniqueId().equals(current.getHighestBidderUuid())) {
                    viewer.sendMessage(plugin.getLangManager().prefixed("bid.already-highest"));
                    new MainMenu(plugin, viewer).open();
                    return;
                }

                double currentMin = plugin.getAuctionManager().getMinBid(current);
                if (amount < currentMin) {
                    viewer.sendMessage(plugin.getLangManager().prefixed("bid.bid-too-low",
                            "{min}", plugin.getEconomyManager().format(currentMin, current.getCurrency())));
                    new MainMenu(plugin, viewer).open();
                    return;
                }

                if (!plugin.getEconomyManager().has(viewer, amount, current.getCurrency())) {
                    viewer.sendMessage(plugin.getLangManager().prefixed("auction.not-enough-money",
                            "{price}", plugin.getEconomyManager().format(amount, current.getCurrency())));
                    new MainMenu(plugin, viewer).open();
                    return;
                }

                boolean success = plugin.getAuctionManager().placeBid(viewer, current.getId(), amount);
                if (success) {
                    viewer.sendMessage(plugin.getLangManager().prefixed("bid.placed",
                            "{item}", AuctionManager.getItemName(current.getItemStack()),
                            "{amount}", plugin.getEconomyManager().format(amount, current.getCurrency())));
                } else {
                    viewer.sendMessage(plugin.getLangManager().prefixed("auction.auction-not-found"));
                }

                new MainMenu(plugin, viewer).open();
            });
        });
    }
}
