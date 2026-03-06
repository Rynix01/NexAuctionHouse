package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.TransactionLog;
import net.nexuby.nexauctionhouse.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HistoryGui extends PaginatedGui {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final UUID targetUuid;
    private final String targetName;

    private int backSlot = -1;
    private int closeSlot = -1;

    /**
     * Opens a history GUI for the viewer showing their own history.
     */
    public HistoryGui(NexAuctionHouse plugin, Player viewer) {
        this(plugin, viewer, viewer.getUniqueId(), viewer.getName());
    }

    /**
     * Opens a history GUI showing a specific player's history (admin use).
     */
    public HistoryGui(NexAuctionHouse plugin, Player viewer, UUID targetUuid, String targetName) {
        super(plugin, viewer);
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    @Override
    protected String getGuiConfigName() {
        return "history";
    }

    @Override
    protected List<ItemStack> getDisplayItems() {
        int limit = plugin.getConfigManager().getHistoryLimit();
        List<TransactionLog> logs = plugin.getAuctionManager().getDao().getPlayerHistory(targetUuid, limit);

        FileConfiguration cfg = plugin.getGuiConfig().getGui(getGuiConfigName());
        List<String> saleLore = cfg != null ? cfg.getStringList("sale-item-lore") : List.of();
        List<String> purchaseLore = cfg != null ? cfg.getStringList("purchase-item-lore") : List.of();

        List<ItemStack> displayItems = new ArrayList<>();

        for (TransactionLog log : logs) {
            ItemStack display = log.getItemStack().clone();
            ItemMeta meta = display.getItemMeta();

            List<Component> existingLore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();

            boolean isSeller = log.getSellerUuid().equals(targetUuid);
            List<String> template = isSeller ? saleLore : purchaseLore;

            String buyerName = log.getBuyerUuid() != null
                    ? Bukkit.getOfflinePlayer(log.getBuyerUuid()).getName() : "-";
            String sellerName = Bukkit.getOfflinePlayer(log.getSellerUuid()).getName();
            if (buyerName == null) buyerName = "Unknown";
            if (sellerName == null) sellerName = "Unknown";

            long elapsed = System.currentTimeMillis() - log.getTimestamp();
            String timeAgo = TimeUtil.formatDuration(elapsed / 1000);

            for (String line : template) {
                String parsed = line
                        .replace("{buyer}", buyerName)
                        .replace("{seller}", sellerName)
                        .replace("{price}", plugin.getEconomyManager().format(log.getPrice()))
                        .replace("{tax}", plugin.getEconomyManager().format(log.getTaxAmount()))
                        .replace("{revenue}", plugin.getEconomyManager().format(log.getPrice() - log.getTaxAmount()))
                        .replace("{time}", timeAgo)
                        .replace("{action}", log.getAction());
                existingLore.add(text(parsed));
            }

            meta.lore(existingLore);
            display.setItemMeta(meta);

            displayItems.add(display);
        }

        return displayItems;
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, int itemIndex) {
        // Right-click opens item preview
        if (event.isRightClick()) {
            List<TransactionLog> logs = plugin.getAuctionManager().getDao()
                    .getPlayerHistory(targetUuid, plugin.getConfigManager().getHistoryLimit());
            if (itemIndex < logs.size()) {
                new PreviewGui(plugin, viewer, logs.get(itemIndex).getItemStack(),
                        () -> new HistoryGui(plugin, viewer, targetUuid, targetName).open()).open();
            }
        }
    }

    @Override
    protected void addExtraButtons(FileConfiguration cfg) {
        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons == null) return;

        if (buttons.contains("back")) {
            backSlot = buttons.getInt("back.slot", -1);
            if (backSlot >= 0) {
                inventory.setItem(backSlot, createButton(buttons.getConfigurationSection("back")));
            }
        }

        if (buttons.contains("close")) {
            closeSlot = buttons.getInt("close.slot", -1);
            if (closeSlot >= 0) {
                inventory.setItem(closeSlot, createButton(buttons.getConfigurationSection("close")));
            }
        }
    }

    @Override
    protected void handleExtraClick(InventoryClickEvent event, int slot) {
        if (slot == backSlot) {
            new MainMenu(plugin, viewer).open();
        } else if (slot == closeSlot) {
            viewer.closeInventory();
        }
    }
}
