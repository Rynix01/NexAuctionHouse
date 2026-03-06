package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Admin GUI - Shows all active auctions with the ability to remove/return any item.
 * Optionally filtered by a specific player name.
 */
public class AdminGui extends PaginatedGui {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final List<Integer> auctionIds = new ArrayList<>();
    private final String targetPlayer; // null = show all

    private int closeSlot = -1;
    private int refreshSlot = -1;

    public AdminGui(NexAuctionHouse plugin, Player viewer) {
        this(plugin, viewer, null);
    }

    public AdminGui(NexAuctionHouse plugin, Player viewer, String targetPlayer) {
        super(plugin, viewer);
        this.targetPlayer = targetPlayer;
    }

    @Override
    protected String getGuiConfigName() {
        return "main-menu";
    }

    @Override
    protected void build() {
        FileConfiguration cfg = plugin.getGuiConfig().getGui(getGuiConfigName());
        if (cfg == null) return;

        int size = cfg.getInt("size", 54);

        String title = targetPlayer != null
                ? "<dark_red>Admin <dark_gray>- " + targetPlayer
                : "<dark_red>Admin <dark_gray>- Auction House";

        inventory = Bukkit.createInventory(this, size, text(title));

        itemSlots = cfg.getIntegerList("item-slots");

        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons != null) {
            if (buttons.contains("previous-page.slot"))
                prevPageSlot = buttons.getInt("previous-page.slot");
            if (buttons.contains("next-page.slot"))
                nextPageSlot = buttons.getInt("next-page.slot");
        }

        pageItems = getDisplayItems();

        int startIndex = currentPage * itemSlots.size();
        int endIndex = Math.min(startIndex + itemSlots.size(), pageItems.size());

        for (int i = 0; i < itemSlots.size(); i++) {
            int dataIndex = startIndex + i;
            if (dataIndex < endIndex) {
                inventory.setItem(itemSlots.get(i), pageItems.get(dataIndex));
            }
        }

        if (buttons != null) {
            if (prevPageSlot >= 0 && currentPage > 0)
                inventory.setItem(prevPageSlot, createButton(buttons.getConfigurationSection("previous-page")));
            if (nextPageSlot >= 0 && endIndex < pageItems.size())
                inventory.setItem(nextPageSlot, createButton(buttons.getConfigurationSection("next-page")));
        }

        addExtraButtons(cfg);
        applyFiller(cfg);
    }

    @Override
    protected List<ItemStack> getDisplayItems() {
        auctionIds.clear();

        AuctionManager manager = plugin.getAuctionManager();
        List<AuctionItem> auctions = new ArrayList<>(manager.getActiveAuctionsList());
        auctions.removeIf(AuctionItem::isExpired);

        // Filter by player if specified
        if (targetPlayer != null) {
            auctions.removeIf(a -> !a.getSellerName().equalsIgnoreCase(targetPlayer));
        }

        auctions.sort(Comparator.comparingLong(AuctionItem::getCreatedAt).reversed());

        List<ItemStack> displayItems = new ArrayList<>();

        for (AuctionItem auction : auctions) {
            ItemStack display = auction.getItemStack().clone();
            ItemMeta meta = display.getItemMeta();

            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
            lore.add(text("<gray>Seller: <white>" + auction.getSellerName()));
            lore.add(text("<gray>Price: <green>" + plugin.getEconomyManager().format(auction.getPrice(), auction.getCurrency())));
            lore.add(text("<gray>Expires in: <yellow>" + TimeUtil.formatDuration(auction.getRemainingTime())));
            lore.add(text("<gray>Tax: <red>" + String.format("%.1f%%", auction.getTaxRate())));
            lore.add(text("<gray>Auction ID: <white>#" + auction.getId()));
            lore.add(Component.empty());
            lore.add(text("<red><bold>LEFT CLICK</bold> <red>to remove and return to seller"));
            lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));

            meta.lore(lore);
            display.setItemMeta(meta);
            displayItems.add(display);
            auctionIds.add(auction.getId());
        }

        return displayItems;
    }

    @Override
    protected void onItemClick(InventoryClickEvent event, int itemIndex) {
        if (itemIndex >= auctionIds.size()) return;

        int auctionId = auctionIds.get(itemIndex);
        AuctionItem auction = plugin.getAuctionManager().getAuction(auctionId);

        if (auction == null) {
            viewer.sendMessage(plugin.getLangManager().prefixed("auction.auction-not-found"));
            refresh();
            return;
        }

        // Right-click opens item preview
        if (event.isRightClick()) {
            new PreviewGui(plugin, viewer, auction.getItemStack(),
                    () -> new AdminGui(plugin, viewer).open()).open();
            return;
        }

        // Admin force-remove: item is returned to seller directly or via expired items
        boolean removed = plugin.getAuctionManager().cancelAuction(viewer, auctionId, true);

        if (removed) {
            viewer.sendMessage(plugin.getLangManager().prefixed("admin.removed",
                    "{player}", auction.getSellerName()));

            // Notify seller if online
            Player seller = Bukkit.getPlayer(auction.getSellerUuid());
            if (seller != null && seller.isOnline()) {
                seller.sendMessage(plugin.getLangManager().prefixed("admin.force-removed",
                        "{item}", AuctionManager.getItemName(auction.getItemStack())));
            }
        }

        refresh();
    }

    @Override
    protected void addExtraButtons(FileConfiguration cfg) {
        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons == null) return;

        if (buttons.contains("refresh")) {
            refreshSlot = buttons.getInt("refresh.slot", -1);
            if (refreshSlot >= 0)
                inventory.setItem(refreshSlot, createButton(buttons.getConfigurationSection("refresh")));
        }

        if (buttons.contains("close")) {
            closeSlot = buttons.getInt("close.slot", -1);
            if (closeSlot >= 0)
                inventory.setItem(closeSlot, createButton(buttons.getConfigurationSection("close")));
        }
    }

    @Override
    protected void handleExtraClick(InventoryClickEvent event, int slot) {
        if (slot == refreshSlot) {
            refresh();
        } else if (slot == closeSlot) {
            viewer.closeInventory();
        }
    }
}
