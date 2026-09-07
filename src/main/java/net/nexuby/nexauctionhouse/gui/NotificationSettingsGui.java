package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.NotificationManager;
import net.nexuby.nexauctionhouse.model.NotificationSettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Player notification preferences, fully laid out through gui/notifications.yml. */
public class NotificationSettingsGui extends AbstractGui {

    private int saleSlot = -1;
    private int bidSlot = -1;
    private int soundSlot = -1;
    private int loginSlot = -1;
    private int favoriteSlot = -1;
    private int allSlot = -1;
    private int themeSlot = -1;
    private int backSlot = -1;

    private final Runnable backAction;

    public NotificationSettingsGui(NexAuctionHouse plugin, Player viewer, Runnable backAction) {
        super(plugin, viewer);
        this.backAction = backAction;
    }

    @Override
    protected void build() {
        FileConfiguration cfg = plugin.getGuiConfig().getGui("notifications");
        if (cfg == null) {
            plugin.getLogger().warning("GUI config 'notifications' not found!");
            return;
        }

        inventory = Bukkit.createInventory(this, cfg.getInt("size", 54),
                text(cfg.getString("title", "<dark_gray>Notification Settings")));

        NotificationSettings settings = plugin.getNotificationManager().getSettings(viewer.getUniqueId());
        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        if (buttons == null) {
            applyFiller(cfg);
            return;
        }

        placeButton(buttons, "header");
        saleSlot = placeToggle(buttons, "sale", settings.isSaleNotifications());
        bidSlot = placeToggle(buttons, "bid", settings.isBidNotifications());
        soundSlot = placeToggle(buttons, "sound", settings.isSoundEffects());
        loginSlot = placeToggle(buttons, "login", settings.isLoginNotifications());
        favoriteSlot = placeToggle(buttons, "favorite", settings.isFavoriteNotifications());
        allSlot = placeToggle(buttons, "all", settings.areAllEnabled());

        ConfigurationSection theme = buttons.getConfigurationSection("theme");
        if (theme != null) {
            themeSlot = theme.getInt("slot", -1);
            String currentTheme = plugin.getThemeManager() != null
                    ? plugin.getThemeManager().getThemeName(
                    plugin.getThemeManager().getPlayerTheme(viewer.getUniqueId()))
                    : "Default";
            place(themeSlot, createConfiguredItem(theme, theme.getString("material", "PAINTING"),
                    "{theme}", currentTheme));
        }

        backSlot = placeButton(buttons, "back");
        applyFiller(cfg);
    }

    private int placeButton(ConfigurationSection buttons, String key) {
        ConfigurationSection section = buttons.getConfigurationSection(key);
        if (section == null) return -1;
        int slot = section.getInt("slot", -1);
        place(slot, createConfiguredItem(section, section.getString("material", "STONE")));
        return slot;
    }

    private int placeToggle(ConfigurationSection buttons, String key, boolean enabled) {
        ConfigurationSection section = buttons.getConfigurationSection(key);
        if (section == null) return -1;
        int slot = section.getInt("slot", -1);
        String material = section.getString(enabled ? "enabled-material" : "disabled-material",
                enabled ? section.getString("material", "LIME_DYE") : "GRAY_DYE");
        String status = plugin.getLangManager().getRaw(
                enabled ? "notifications.enabled" : "notifications.disabled");
        place(slot, createConfiguredItem(section, material, "{status}", status));
        return slot;
    }

    private ItemStack createConfiguredItem(ConfigurationSection section, String materialName,
                                           String... replacements) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.STONE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (section.contains("name")) {
            meta.displayName(text(replace(section.getString("name", " "), replacements)));
        }
        if (section.contains("lore")) {
            List<Component> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(text(replace(line, replacements)));
            }
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String replace(String value, String... replacements) {
        String result = value;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace(replacements[i], replacements[i + 1]);
        }
        return result;
    }

    private void place(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize() || !checkCooldown(viewer)) return;

        String toggleKey = null;
        if (slot == saleSlot) toggleKey = "sale";
        else if (slot == bidSlot) toggleKey = "bid";
        else if (slot == soundSlot) toggleKey = "sound";
        else if (slot == loginSlot) toggleKey = "login";
        else if (slot == favoriteSlot) toggleKey = "favorite";
        else if (slot == allSlot) toggleKey = "all";

        if (toggleKey != null) {
            NotificationManager manager = plugin.getNotificationManager();
            NotificationSettings settings = manager.getSettings(viewer.getUniqueId());
            boolean enabled = settings.toggle(toggleKey);
            manager.saveSettings(settings);

            if (manager.hasSoundEnabled(viewer.getUniqueId())) {
                viewer.playSound(viewer.getLocation(),
                        enabled ? org.bukkit.Sound.UI_BUTTON_CLICK : org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS,
                        0.5f, enabled ? 1.2f : 0.8f);
            }

            viewer.sendMessage(plugin.getLangManager().prefixed("notifications.toggled",
                    "{setting}", plugin.getLangManager().getRaw("notifications." + toggleKey),
                    "{status}", plugin.getLangManager().getRaw(
                            enabled ? "notifications.enabled" : "notifications.disabled")));
            build();
            viewer.openInventory(inventory);
            return;
        }

        if (slot == themeSlot) {
            new ThemeSelectGui(plugin, viewer, () ->
                    new NotificationSettingsGui(plugin, viewer, backAction).open()).open();
        } else if (slot == backSlot) {
            if (backAction != null) backAction.run();
            else viewer.closeInventory();
        }
    }
}
