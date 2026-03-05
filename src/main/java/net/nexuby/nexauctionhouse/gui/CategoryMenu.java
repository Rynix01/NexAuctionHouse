package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Shows category buttons. When a category is clicked, it opens the MainMenu
 * filtered to only show items matching that category.
 */
public class CategoryMenu extends AbstractGui {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<Integer, CategoryEntry> categorySlots = new HashMap<>();
    private int backSlot = -1;

    public CategoryMenu(NexAuctionHouse plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected void build() {
        FileConfiguration cfg = plugin.getGuiConfig().getGui("categories");
        if (cfg == null) {
            plugin.getLogger().warning("GUI config 'categories' not found!");
            return;
        }

        String title = cfg.getString("title", "<dark_gray>Categories");
        int size = cfg.getInt("size", 36);

        inventory = Bukkit.createInventory(this, size, text(title));

        // Load categories
        ConfigurationSection categoriesSection = cfg.getConfigurationSection("categories");
        if (categoriesSection != null) {
            for (String key : categoriesSection.getKeys(false)) {
                ConfigurationSection cat = categoriesSection.getConfigurationSection(key);
                if (cat == null) continue;

                int slot = cat.getInt("slot", -1);
                if (slot < 0) continue;

                ItemStack icon = createButton(cat);
                inventory.setItem(slot, icon);

                // Build filter based on config
                String filterType = cat.getString("filter-type", "");
                List<String> filterPatterns = cat.getStringList("filter");

                Predicate<AuctionItem> filter = buildFilter(filterType, filterPatterns);
                categorySlots.put(slot, new CategoryEntry(key, filter));
            }
        }

        // Back button
        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons != null && buttons.contains("back")) {
            backSlot = buttons.getInt("back.slot", -1);
            if (backSlot >= 0) {
                inventory.setItem(backSlot, createButton(buttons.getConfigurationSection("back")));
            }
        }

        applyFiller(cfg);
    }

    private Predicate<AuctionItem> buildFilter(String filterType, List<String> patterns) {
        if (filterType.equalsIgnoreCase("block")) {
            return item -> item.getItemStack().getType().isBlock();
        } else if (filterType.equalsIgnoreCase("food")) {
            return item -> item.getItemStack().getType().isEdible();
        } else if (filterType.equalsIgnoreCase("misc")) {
            // Misc = items that don't match any specific category
            return item -> {
                Material mat = item.getItemStack().getType();
                return !mat.isBlock() && !mat.isEdible();
            };
        } else if (!patterns.isEmpty()) {
            // Match by material name patterns (e.g. "_SWORD", "_HELMET")
            return item -> {
                String materialName = item.getItemStack().getType().name();
                for (String pattern : patterns) {
                    if (materialName.contains(pattern)) {
                        return true;
                    }
                }
                return false;
            };
        }

        // No filter = show all
        return item -> true;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!checkCooldown((Player) event.getWhoClicked())) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        if (slot == backSlot) {
            new MainMenu(plugin, viewer).open();
            return;
        }

        CategoryEntry entry = categorySlots.get(slot);
        if (entry != null) {
            new MainMenu(plugin, viewer, entry.filter(), entry.name()).open();
        }
    }

    private record CategoryEntry(String name, Predicate<AuctionItem> filter) {}
}
