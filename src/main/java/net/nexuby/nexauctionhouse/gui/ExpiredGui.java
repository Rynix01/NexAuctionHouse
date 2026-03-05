package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.database.AuctionDAO;
import net.nexuby.nexauctionhouse.model.ExpiredItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ExpiredGui extends PaginatedGui {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final List<ExpiredItem> expiredItems = new ArrayList<>();

    private int collectAllSlot = -1;
    private int backSlot = -1;
    private int closeSlot = -1;

    public ExpiredGui(NexAuctionHouse plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String getGuiConfigName() {
        return "expired";
    }

    @Override
    protected List<ItemStack> getDisplayItems() {
        expiredItems.clear();

        AuctionDAO dao = plugin.getAuctionManager().getDao();
        List<ExpiredItem> items = dao.getExpiredItems(viewer.getUniqueId());
        expiredItems.addAll(items);

        FileConfiguration cfg = plugin.getGuiConfig().getGui(getGuiConfigName());
        List<String> loreTemplate = cfg != null ? cfg.getStringList("expired-item-lore") : List.of();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        List<ItemStack> displayItems = new ArrayList<>();

        for (ExpiredItem expired : expiredItems) {
            ItemStack display = expired.getItemStack().clone();
            ItemMeta meta = display.getItemMeta();

            List<Component> existingLore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();

            for (String line : loreTemplate) {
                String parsed = line
                        .replace("{price}", "N/A")
                        .replace("{date}", dateFormat.format(new Date(expired.getCreatedAt())));
                existingLore.add(mm.deserialize(parsed));
            }

            meta.lore(existingLore);
            display.setItemMeta(meta);
            displayItems.add(display);
        }

        return displayItems;
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, int itemIndex) {
        if (itemIndex >= expiredItems.size()) return;

        ExpiredItem expired = expiredItems.get(itemIndex);
        collectItem(expired);
        refresh();
    }

    private void collectItem(ExpiredItem expired) {
        if (viewer.getInventory().firstEmpty() == -1) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.inventory-full"));
            return;
        }

        viewer.getInventory().addItem(expired.getItemStack());
        plugin.getAuctionManager().getDao().deleteExpiredItem(expired.getId());
    }

    @Override
    protected void addExtraButtons(FileConfiguration cfg) {
        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons == null) return;

        if (buttons.contains("collect-all")) {
            collectAllSlot = buttons.getInt("collect-all.slot", -1);
            if (collectAllSlot >= 0) {
                inventory.setItem(collectAllSlot, createButton(buttons.getConfigurationSection("collect-all")));
            }
        }

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
        if (slot == collectAllSlot) {
            collectAll();
            refresh();
        } else if (slot == backSlot) {
            new MainMenu(plugin, viewer).open();
        } else if (slot == closeSlot) {
            viewer.closeInventory();
        }
    }

    private void collectAll() {
        int collected = 0;

        for (ExpiredItem expired : new ArrayList<>(expiredItems)) {
            if (viewer.getInventory().firstEmpty() == -1) {
                break;
            }
            viewer.getInventory().addItem(expired.getItemStack());
            plugin.getAuctionManager().getDao().deleteExpiredItem(expired.getId());
            collected++;
        }

        if (collected > 0) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.collected",
                    "{amount}", String.valueOf(collected)));
        }
    }
}
