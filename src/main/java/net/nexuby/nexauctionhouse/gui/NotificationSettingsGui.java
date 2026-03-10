package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.NotificationManager;
import net.nexuby.nexauctionhouse.model.NotificationSettings;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class NotificationSettingsGui extends AbstractGui {

    private static final int SALE_SLOT = 20;
    private static final int BID_SLOT = 22;
    private static final int SOUND_SLOT = 24;
    private static final int LOGIN_SLOT = 30;
    private static final int FAVORITE_SLOT = 32;
    private static final int THEME_SLOT = 40;
    private static final int BACK_SLOT = 49;

    private final Runnable backAction;

    public NotificationSettingsGui(NexAuctionHouse plugin, Player viewer, Runnable backAction) {
        super(plugin, viewer);
        this.backAction = backAction;
        this.inventory = org.bukkit.Bukkit.createInventory(this, 54, text("<dark_gray>Notification Settings"));
    }

    @Override
    protected void build() {
        NotificationManager nm = plugin.getNotificationManager();
        NotificationSettings settings = nm.getSettings(viewer.getUniqueId());

        // Fill background using player's theme
        ItemStack filler = createThemedFiller();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // Title item
        ItemStack titleItem = new ItemStack(Material.BELL);
        ItemMeta titleMeta = titleItem.getItemMeta();
        titleMeta.displayName(text("<gold>Notification Preferences"));
        titleMeta.lore(List.of(
                text("<gray>Toggle your notification settings."),
                text("<gray>Click any option to toggle.")
        ));
        titleItem.setItemMeta(titleMeta);
        inventory.setItem(4, titleItem);

        // Toggle buttons
        setToggle(SALE_SLOT, Material.GOLD_INGOT, "<yellow>Sale Notifications",
                "<gray>Receive notifications when your", "<gray>items are sold.",
                settings.isSaleNotifications());

        setToggle(BID_SLOT, Material.DIAMOND, "<aqua>Bid Notifications",
                "<gray>Receive notifications when someone", "<gray>bids on your auctions.",
                settings.isBidNotifications());

        setToggle(SOUND_SLOT, Material.NOTE_BLOCK, "<green>Sound Effects",
                "<gray>Play sound effects for auction", "<gray>events and notifications.",
                settings.isSoundEffects());

        setToggle(LOGIN_SLOT, Material.OAK_DOOR, "<white>Login Notifications",
                "<gray>Receive notifications about pending", "<gray>items and revenue on login.",
                settings.isLoginNotifications());

        setToggle(FAVORITE_SLOT, Material.NETHER_STAR, "<light_purple>Favorite Notifications",
                "<gray>Receive notifications when favorited", "<gray>items are sold, cancelled, or expired.",
                settings.isFavoriteNotifications());

        // Theme selection button
        String currentTheme = plugin.getThemeManager() != null
                ? plugin.getThemeManager().getThemeName(plugin.getThemeManager().getPlayerTheme(viewer.getUniqueId()))
                : "Default";
        ItemStack themeItem = new ItemStack(Material.PAINTING);
        ItemMeta themeMeta = themeItem.getItemMeta();
        themeMeta.displayName(text("<gold>GUI Theme"));
        themeMeta.lore(List.of(
                text("<gray>Customize the look of your menus."),
                text(""),
                text("<gray>Current: <yellow>" + currentTheme),
                text(""),
                text("<yellow>Click to browse themes.")
        ));
        themeItem.setItemMeta(themeMeta);
        inventory.setItem(THEME_SLOT, themeItem);

        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(text("<yellow>Back"));
        backMeta.lore(List.of(text("<gray>Return to previous menu.")));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);
    }

    private void setToggle(int slot, Material material, String name,
                           String desc1, String desc2, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? material : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name));

        String status = enabled ? "<green>ENABLED" : "<red>DISABLED";
        meta.lore(List.of(
                text(desc1),
                text(desc2),
                text(""),
                text("<gray>Status: " + status),
                text(""),
                text("<yellow>Click to toggle.")
        ));

        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        NotificationManager nm = plugin.getNotificationManager();
        NotificationSettings settings = nm.getSettings(viewer.getUniqueId());

        String toggleKey = switch (slot) {
            case SALE_SLOT -> "sale";
            case BID_SLOT -> "bid";
            case SOUND_SLOT -> "sound";
            case LOGIN_SLOT -> "login";
            case FAVORITE_SLOT -> "favorite";
            default -> null;
        };

        if (toggleKey != null) {
            boolean newValue = settings.toggle(toggleKey);
            nm.saveSettings(settings);

            // Play feedback sound if sound is enabled (or was just enabled)
            if (nm.hasSoundEnabled(viewer.getUniqueId())) {
                viewer.playSound(viewer.getLocation(),
                        newValue ? org.bukkit.Sound.UI_BUTTON_CLICK : org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS,
                        0.5f, newValue ? 1.2f : 0.8f);
            }

            viewer.sendMessage(plugin.getLangManager().prefixed("notifications.toggled",
                    "{setting}", plugin.getLangManager().getRaw("notifications." + toggleKey),
                    "{status}", plugin.getLangManager().getRaw(newValue ? "notifications.enabled" : "notifications.disabled")));

            // Rebuild the GUI in place
            build();
            viewer.openInventory(inventory);
            return;
        }

        if (slot == BACK_SLOT) {
            if (backAction != null) {
                backAction.run();
            } else {
                viewer.closeInventory();
            }
        }

        if (slot == THEME_SLOT) {
            new ThemeSelectGui(plugin, viewer, () ->
                    new NotificationSettingsGui(plugin, viewer, backAction).open()).open();
        }
    }
}
