package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.ThemeManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ThemeSelectGui extends AbstractGui {

    private static final int BACK_SLOT = 49;

    private final Runnable backAction;
    private final List<String> themeIds = new ArrayList<>();

    public ThemeSelectGui(NexAuctionHouse plugin, Player viewer, Runnable backAction) {
        super(plugin, viewer);
        this.backAction = backAction;
    }

    @Override
    protected void build() {
        ThemeManager tm = plugin.getThemeManager();
        String currentTheme = tm.getPlayerTheme(viewer.getUniqueId());

        inventory = Bukkit.createInventory(this, 54, text("<dark_gray>Select Theme"));

        // Fill background with player's current theme filler
        Material fillerMat = tm.getFillerMaterial(viewer.getUniqueId());
        ItemStack filler = new ItemStack(fillerMat);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // Title item
        ItemStack titleItem = new ItemStack(Material.PAINTING);
        ItemMeta titleMeta = titleItem.getItemMeta();
        titleMeta.displayName(text("<gold>GUI Themes"));
        titleMeta.lore(List.of(
                text("<gray>Select a theme to customize"),
                text("<gray>the look of your menus."),
                text(""),
                text("<gray>Current: <yellow>" + tm.getThemeName(currentTheme))
        ));
        titleItem.setItemMeta(titleMeta);
        inventory.setItem(4, titleItem);

        // Place theme items starting at slot 20
        themeIds.clear();
        int[] slots = {20, 22, 24};
        int index = 0;

        for (String themeId : tm.getThemeIds()) {
            if (index >= slots.length) break;

            themeIds.add(themeId);
            boolean selected = themeId.equalsIgnoreCase(currentTheme);

            Material icon = tm.getThemeIcon(themeId);
            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();

            String themeName = tm.getThemeName(themeId);
            String desc = tm.getThemeDescription(themeId);

            if (selected) {
                meta.displayName(text("<green>" + themeName + " <gray>(Selected)"));
                meta.setEnchantmentGlintOverride(true);
            } else {
                meta.displayName(text("<yellow>" + themeName));
            }

            List<Component> lore = new ArrayList<>();
            lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
            if (!desc.isEmpty()) {
                lore.add(text("<gray>" + desc));
                lore.add(text(""));
            }

            // Show preview of filler material
            Material previewMat = Material.matchMaterial(
                    tm.getTheme(themeId).getString("filler.material", "BLACK_STAINED_GLASS_PANE"));
            if (previewMat != null) {
                lore.add(text("<gray>Background: <white>" + formatMaterial(previewMat)));
            }

            lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));

            if (selected) {
                lore.add(text("<green>Currently selected!"));
            } else {
                lore.add(text("<yellow>Click to select this theme."));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slots[index], item);
            index++;
        }

        // If there are more than 3 themes, place them in the next row
        if (tm.getThemeIds().size() > 3) {
            int[] extraSlots = {29, 30, 31, 32, 33};
            int extraIndex = 0;
            int totalIndex = 0;

            for (String themeId : tm.getThemeIds()) {
                totalIndex++;
                if (totalIndex <= 3) continue; // Already placed above
                if (extraIndex >= extraSlots.length) break;

                themeIds.add(themeId);
                boolean selected = themeId.equalsIgnoreCase(currentTheme);

                Material icon = tm.getThemeIcon(themeId);
                ItemStack item = new ItemStack(icon);
                ItemMeta meta = item.getItemMeta();

                String themeName = tm.getThemeName(themeId);

                if (selected) {
                    meta.displayName(text("<green>" + themeName + " <gray>(Selected)"));
                    meta.setEnchantmentGlintOverride(true);
                } else {
                    meta.displayName(text("<yellow>" + themeName));
                }

                List<Component> lore = new ArrayList<>();
                lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
                String desc = tm.getThemeDescription(themeId);
                if (!desc.isEmpty()) {
                    lore.add(text("<gray>" + desc));
                }
                lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));

                if (selected) {
                    lore.add(text("<green>Currently selected!"));
                } else {
                    lore.add(text("<yellow>Click to select this theme."));
                }

                meta.lore(lore);
                item.setItemMeta(meta);
                inventory.setItem(extraSlots[extraIndex], item);
                extraIndex++;
            }
        }

        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(text("<yellow>Back"));
        backMeta.lore(List.of(text("<gray>Return to previous menu.")));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!checkCooldown(viewer)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        if (slot == BACK_SLOT) {
            if (backAction != null) {
                backAction.run();
            } else {
                viewer.closeInventory();
            }
            return;
        }

        // Check if a theme slot was clicked
        ThemeManager tm = plugin.getThemeManager();
        int[] firstRowSlots = {20, 22, 24};
        int[] secondRowSlots = {29, 30, 31, 32, 33};

        String clickedTheme = null;

        for (int i = 0; i < firstRowSlots.length; i++) {
            if (slot == firstRowSlots[i] && i < themeIds.size()) {
                clickedTheme = themeIds.get(i);
                break;
            }
        }

        if (clickedTheme == null) {
            for (int i = 0; i < secondRowSlots.length; i++) {
                int themeIndex = 3 + i;
                if (slot == secondRowSlots[i] && themeIndex < themeIds.size()) {
                    clickedTheme = themeIds.get(themeIndex);
                    break;
                }
            }
        }

        if (clickedTheme != null) {
            String current = tm.getPlayerTheme(viewer.getUniqueId());
            if (clickedTheme.equalsIgnoreCase(current)) return; // Already selected

            final String theme = clickedTheme;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    tm.savePlayerTheme(viewer.getUniqueId(), theme));

            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            viewer.sendMessage(plugin.getLangManager().prefixed("theme.selected",
                    "{theme}", tm.getThemeName(clickedTheme)));

            // Rebuild to reflect new theme immediately
            build();
            viewer.openInventory(inventory);
        }
    }

    private String formatMaterial(Material material) {
        String name = material.name().replace("_", " ");
        StringBuilder formatted = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!formatted.isEmpty()) formatted.append(" ");
            formatted.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return formatted.toString();
    }
}
