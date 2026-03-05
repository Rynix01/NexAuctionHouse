package net.nexuby.nexauctionhouse.gui;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Extends AbstractGui with pagination support.
 * Subclasses provide the full item list, and this class handles page slicing and navigation.
 */
public abstract class PaginatedGui extends AbstractGui {

    protected int currentPage = 0;
    protected List<ItemStack> pageItems;
    protected List<Integer> itemSlots;

    // Button slots read from config
    protected int prevPageSlot = -1;
    protected int nextPageSlot = -1;

    public PaginatedGui(NexAuctionHouse plugin, Player viewer) {
        super(plugin, viewer);
    }

    /**
     * Subclasses must return the config name for their GUI (e.g. "main-menu").
     */
    protected abstract String getGuiConfigName();

    /**
     * Subclasses must provide the full list of items to paginate.
     */
    protected abstract List<ItemStack> getDisplayItems();

    /**
     * Called when a non-navigation, non-filler slot is clicked.
     * The index is the item's position in the full getDisplayItems() list.
     */
    protected abstract void onItemClick(InventoryClickEvent event, int itemIndex);

    /**
     * Subclasses can add extra buttons beyond pagination controls.
     */
    protected void addExtraButtons(FileConfiguration cfg) {
        // Default: no extra buttons
    }

    @Override
    protected void build() {
        FileConfiguration cfg = plugin.getGuiConfig().getGui(getGuiConfigName());
        if (cfg == null) {
            plugin.getLogger().warning("GUI config '" + getGuiConfigName() + "' not found!");
            return;
        }

        String title = cfg.getString("title", getGuiConfigName());
        int size = cfg.getInt("size", 54);

        inventory = Bukkit.createInventory(this,
                size,
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(title));

        // Load item slots from config
        itemSlots = cfg.getIntegerList("item-slots");

        // Load navigation button positions
        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons != null) {
            if (buttons.contains("previous-page.slot")) {
                prevPageSlot = buttons.getInt("previous-page.slot");
            }
            if (buttons.contains("next-page.slot")) {
                nextPageSlot = buttons.getInt("next-page.slot");
            }
        }

        // Get items for display
        pageItems = getDisplayItems();

        // Populate the current page
        populatePage(cfg);

        // Apply filler
        applyFiller(cfg);
    }

    private void populatePage(FileConfiguration cfg) {
        int startIndex = currentPage * itemSlots.size();
        int endIndex = Math.min(startIndex + itemSlots.size(), pageItems.size());

        // Place items in their slots
        for (int i = 0; i < itemSlots.size(); i++) {
            int dataIndex = startIndex + i;
            if (dataIndex < endIndex) {
                inventory.setItem(itemSlots.get(i), pageItems.get(dataIndex));
            }
        }

        // Navigation buttons
        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons != null) {
            // Previous page button - only show if not on first page
            if (prevPageSlot >= 0 && currentPage > 0) {
                inventory.setItem(prevPageSlot, createButton(buttons.getConfigurationSection("previous-page")));
            }

            // Next page button - only show if there are more pages
            if (nextPageSlot >= 0 && endIndex < pageItems.size()) {
                inventory.setItem(nextPageSlot, createButton(buttons.getConfigurationSection("next-page")));
            }
        }

        // Let subclasses add their own buttons
        addExtraButtons(cfg);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!checkCooldown((Player) event.getWhoClicked())) {
            return;
        }

        int slot = event.getRawSlot();

        // Ignore clicks outside the GUI inventory
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        // Navigation: previous page
        if (slot == prevPageSlot && currentPage > 0) {
            currentPage--;
            refresh();
            return;
        }

        // Navigation: next page
        int maxPage = getMaxPage();
        if (slot == nextPageSlot && currentPage < maxPage) {
            currentPage++;
            refresh();
            return;
        }

        // Check if it's an item slot
        int slotIndex = itemSlots.indexOf(slot);
        if (slotIndex >= 0) {
            int itemIndex = (currentPage * itemSlots.size()) + slotIndex;
            if (itemIndex < pageItems.size()) {
                onItemClick(event, itemIndex);
            }
            return;
        }

        // Pass to subclass for handling extra buttons
        handleExtraClick(event, slot);
    }

    /**
     * Override this to handle clicks on your extra buttons.
     */
    protected void handleExtraClick(InventoryClickEvent event, int slot) {
        // Default: nothing
    }

    /**
     * Refreshes the GUI contents without closing and reopening.
     */
    public void refresh() {
        inventory.clear();
        pageItems = getDisplayItems();

        FileConfiguration cfg = plugin.getGuiConfig().getGui(getGuiConfigName());
        if (cfg != null) {
            populatePage(cfg);
            applyFiller(cfg);
        }
    }

    protected int getMaxPage() {
        if (pageItems.isEmpty() || itemSlots.isEmpty()) return 0;
        return (pageItems.size() - 1) / itemSlots.size();
    }

    protected int getCurrentPage() {
        return currentPage;
    }
}
