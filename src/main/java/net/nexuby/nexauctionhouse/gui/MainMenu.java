package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.listener.ChatInputListener;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionType;
import net.nexuby.nexauctionhouse.model.SortType;
import net.nexuby.nexauctionhouse.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class MainMenu extends PaginatedGui {

    private final MiniMessage mm = MiniMessage.miniMessage();

    // Optional filter (for category browsing)
    private Predicate<AuctionItem> filter;
    private String categoryName;

    // Search and sort state
    private String searchQuery;
    private SortType sortType = SortType.NEWEST_FIRST;

    // Button slot positions loaded from config
    private int categoriesSlot = -1;
    private int myAuctionsSlot = -1;
    private int expiredSlot = -1;
    private int refreshSlot = -1;
    private int closeSlot = -1;
    private int searchSlot = -1;
    private int sortSlot = -1;
    private int favoritesSlot = -1;

    // Maps item slot index -> auction id for click handling
    private final List<Integer> auctionIds = new ArrayList<>();

    public MainMenu(NexAuctionHouse plugin, Player viewer) {
        super(plugin, viewer);
    }

    public MainMenu(NexAuctionHouse plugin, Player viewer, Predicate<AuctionItem> filter, String categoryName) {
        super(plugin, viewer);
        this.filter = filter;
        this.categoryName = categoryName;
    }

    public MainMenu(NexAuctionHouse plugin, Player viewer, String searchQuery) {
        super(plugin, viewer);
        this.searchQuery = searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
        this.currentPage = 0;
    }

    public void setSortType(SortType sortType) {
        this.sortType = sortType;
    }

    public SortType getSortType() {
        return sortType;
    }

    @Override
    protected String getGuiConfigName() {
        return "main-menu";
    }

    @Override
    protected List<ItemStack> getDisplayItems() {
        auctionIds.clear();

        AuctionManager manager = plugin.getAuctionManager();
        List<AuctionItem> auctions = new ArrayList<>(manager.getActiveAuctionsList());

        // Apply category filter if set
        if (filter != null) {
            auctions.removeIf(item -> !filter.test(item));
        }

        // Apply search filter if set
        if (searchQuery != null && !searchQuery.isBlank()) {
            String query = searchQuery.toLowerCase();
            auctions.removeIf(item -> {
                String itemName = AuctionManager.getItemName(item.getItemStack()).toLowerCase();
                String materialName = item.getItemStack().getType().name().toLowerCase().replace("_", " ");
                String sellerName = item.getSellerName().toLowerCase();
                return !itemName.contains(query) && !materialName.contains(query) && !sellerName.contains(query);
            });
        }

        // Remove expired items from the display
        auctions.removeIf(AuctionItem::isExpired);

        // Sort based on current sort type
        switch (sortType) {
            case NEWEST_FIRST -> auctions.sort(Comparator.comparingLong(AuctionItem::getCreatedAt).reversed());
            case OLDEST_FIRST -> auctions.sort(Comparator.comparingLong(AuctionItem::getCreatedAt));
            case PRICE_LOW_TO_HIGH -> auctions.sort(Comparator.comparingDouble(AuctionItem::getPrice));
            case PRICE_HIGH_TO_LOW -> auctions.sort(Comparator.comparingDouble(AuctionItem::getPrice).reversed());
            case NAME_A_TO_Z -> auctions.sort(Comparator.comparing(a -> AuctionManager.getItemName(a.getItemStack()).toLowerCase()));
            case NAME_Z_TO_A -> auctions.sort(Comparator.comparing((AuctionItem a) -> AuctionManager.getItemName(a.getItemStack()).toLowerCase()).reversed());
        }

        // Read lore template from config
        FileConfiguration cfg = plugin.getGuiConfig().getGui(getGuiConfigName());
        List<String> loreTemplate = cfg != null ? cfg.getStringList("auction-item-lore") : List.of();
        List<String> bidLoreTemplate = cfg != null ? cfg.getStringList("bid-item-lore") : List.of();

        List<ItemStack> displayItems = new ArrayList<>();

        for (AuctionItem auction : auctions) {
            ItemStack display = auction.getItemStack().clone();
            ItemMeta meta = display.getItemMeta();

            // Build the auction info lore
            List<Component> existingLore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();

            List<String> template = auction.isBidAuction() && !bidLoreTemplate.isEmpty() ? bidLoreTemplate : loreTemplate;

            String currentBidStr = auction.getHighestBid() > 0
                    ? plugin.getEconomyManager().format(auction.getHighestBid(), auction.getCurrency())
                    : plugin.getLangManager().getRaw("bid.no-bids-yet");
            String bidderName = auction.getHighestBidderName() != null ? auction.getHighestBidderName() : "-";

            for (String line : template) {
                String parsed = line
                        .replace("{seller}", auction.getSellerName())
                        .replace("{price}", plugin.getEconomyManager().format(auction.getPrice(), auction.getCurrency()))
                        .replace("{time}", TimeUtil.formatDuration(auction.getRemainingTime()))
                        .replace("{current_bid}", currentBidStr)
                        .replace("{bidder}", bidderName)
                        .replace("{type}", auction.isBidAuction() ? "Auction" : "BIN");
                existingLore.add(text(parsed));
            }

            meta.lore(existingLore);
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

        // Shift+click toggles favorite
        if (event.isShiftClick()) {
            AuctionManager manager = plugin.getAuctionManager();
            if (manager.isFavorited(viewer.getUniqueId(), auctionId)) {
                manager.removeFavorite(viewer, auctionId);
                viewer.sendMessage(plugin.getLangManager().prefixed("favorites.removed",
                        "{item}", AuctionManager.getItemName(auction.getItemStack())));
            } else {
                if (manager.addFavorite(viewer, auctionId)) {
                    viewer.sendMessage(plugin.getLangManager().prefixed("favorites.added",
                            "{item}", AuctionManager.getItemName(auction.getItemStack())));
                } else {
                    viewer.sendMessage(plugin.getLangManager().prefixed("favorites.limit-reached",
                            "{limit}", String.valueOf(plugin.getConfigManager().getMaxFavorites())));
                }
            }
            refresh();
            return;
        }

        // Bid auctions open the bid dialog, BIN auctions open confirm purchase
        if (auction.isBidAuction()) {
            new BidGui(plugin, viewer, auction).open();
        } else {
            new ConfirmGui(plugin, viewer, auction).open();
        }
    }

    @Override
    protected void addExtraButtons(FileConfiguration cfg) {
        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons == null) return;

        // Categories button
        if (buttons.contains("categories")) {
            categoriesSlot = buttons.getInt("categories.slot", -1);
            if (categoriesSlot >= 0) {
                inventory.setItem(categoriesSlot, createButton(buttons.getConfigurationSection("categories")));
            }
        }

        // My Auctions button
        if (buttons.contains("my-auctions")) {
            myAuctionsSlot = buttons.getInt("my-auctions.slot", -1);
            if (myAuctionsSlot >= 0) {
                inventory.setItem(myAuctionsSlot, createButton(buttons.getConfigurationSection("my-auctions")));
            }
        }

        // Expired Items button
        if (buttons.contains("expired-items")) {
            expiredSlot = buttons.getInt("expired-items.slot", -1);
            if (expiredSlot >= 0) {
                inventory.setItem(expiredSlot, createButton(buttons.getConfigurationSection("expired-items")));
            }
        }

        // Refresh button
        if (buttons.contains("refresh")) {
            refreshSlot = buttons.getInt("refresh.slot", -1);
            if (refreshSlot >= 0) {
                inventory.setItem(refreshSlot, createButton(buttons.getConfigurationSection("refresh")));
            }
        }

        // Close button
        if (buttons.contains("close")) {
            closeSlot = buttons.getInt("close.slot", -1);
            if (closeSlot >= 0) {
                inventory.setItem(closeSlot, createButton(buttons.getConfigurationSection("close")));
            }
        }

        // Search button
        if (buttons.contains("search")) {
            searchSlot = buttons.getInt("search.slot", -1);
            if (searchSlot >= 0) {
                ItemStack searchButton = createButton(buttons.getConfigurationSection("search"));
                // Show active search in lore if a query is set
                if (searchQuery != null && !searchQuery.isBlank()) {
                    ItemMeta meta = searchButton.getItemMeta();
                    List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                    lore.add(text(""));
                    lore.add(text(plugin.getLangManager().getRaw("search.active-query", "{query}", searchQuery)));
                    lore.add(text(plugin.getLangManager().getRaw("search.click-to-clear")));
                    meta.lore(lore);
                    searchButton.setItemMeta(meta);
                }
                inventory.setItem(searchSlot, searchButton);
            }
        }

        // Sort button
        if (buttons.contains("sort")) {
            sortSlot = buttons.getInt("sort.slot", -1);
            if (sortSlot >= 0) {
                ItemStack sortButton = createButton(buttons.getConfigurationSection("sort"));
                ItemMeta meta = sortButton.getItemMeta();
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(text(""));
                // Show all sort options with indicator for current
                for (SortType type : SortType.values()) {
                    String langKey = "search.sort-" + type.getConfigKey();
                    String label = plugin.getLangManager().getRaw(langKey);
                    if (type == sortType) {
                        lore.add(text("<green>▸ " + label));
                    } else {
                        lore.add(text("<gray>  " + label));
                    }
                }
                meta.lore(lore);
                sortButton.setItemMeta(meta);
                inventory.setItem(sortSlot, sortButton);
            }
        }

        // Favorites button
        if (buttons.contains("favorites")) {
            favoritesSlot = buttons.getInt("favorites.slot", -1);
            if (favoritesSlot >= 0) {
                inventory.setItem(favoritesSlot, createButton(buttons.getConfigurationSection("favorites")));
            }
        }
    }

    @Override
    protected void handleExtraClick(InventoryClickEvent event, int slot) {
        if (slot == categoriesSlot) {
            new CategoryMenu(plugin, viewer).open();
        } else if (slot == myAuctionsSlot) {
            new MyAuctionsGui(plugin, viewer).open();
        } else if (slot == expiredSlot) {
            new ExpiredGui(plugin, viewer).open();
        } else if (slot == refreshSlot) {
            refresh();
        } else if (slot == closeSlot) {
            viewer.closeInventory();
        } else if (slot == searchSlot) {
            handleSearchClick();
        } else if (slot == sortSlot) {
            handleSortClick();
        } else if (slot == favoritesSlot) {
            new FavoritesGui(plugin, viewer).open();
        }
    }

    private void handleSearchClick() {
        // If there's already a search query, right-click clears it
        if (searchQuery != null && !searchQuery.isBlank()) {
            searchQuery = null;
            currentPage = 0;
            refresh();
            return;
        }

        // Close menu and await search input from chat
        viewer.closeInventory();
        viewer.sendMessage(plugin.getLangManager().prefixed("search.enter-query"));
        viewer.sendMessage(plugin.getLangManager().prefixed("auction.type-cancel"));

        MainMenu self = this;
        ChatInputListener.awaitInput(viewer, input -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (input.equalsIgnoreCase("cancel")) {
                    viewer.sendMessage(plugin.getLangManager().prefixed("search.search-cancelled"));
                    new MainMenu(plugin, viewer).open();
                    return;
                }

                self.setSearchQuery(input.trim());
                self.open();
            });
        });
    }

    private void handleSortClick() {
        sortType = sortType.next();
        currentPage = 0;
        refresh();
    }
}
