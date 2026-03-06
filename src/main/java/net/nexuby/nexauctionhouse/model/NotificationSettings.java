package net.nexuby.nexauctionhouse.model;

import java.util.UUID;

public class NotificationSettings {

    private final UUID playerUuid;
    private boolean saleNotifications;
    private boolean bidNotifications;
    private boolean soundEffects;
    private boolean loginNotifications;
    private boolean favoriteNotifications;

    public NotificationSettings(UUID playerUuid, boolean saleNotifications, boolean bidNotifications,
                                boolean soundEffects, boolean loginNotifications, boolean favoriteNotifications) {
        this.playerUuid = playerUuid;
        this.saleNotifications = saleNotifications;
        this.bidNotifications = bidNotifications;
        this.soundEffects = soundEffects;
        this.loginNotifications = loginNotifications;
        this.favoriteNotifications = favoriteNotifications;
    }

    /**
     * Creates default settings with all notifications enabled.
     */
    public static NotificationSettings defaults(UUID playerUuid, boolean sale, boolean bid,
                                                 boolean sound, boolean login, boolean favorite) {
        return new NotificationSettings(playerUuid, sale, bid, sound, login, favorite);
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public boolean isSaleNotifications() {
        return saleNotifications;
    }

    public void setSaleNotifications(boolean saleNotifications) {
        this.saleNotifications = saleNotifications;
    }

    public boolean isBidNotifications() {
        return bidNotifications;
    }

    public void setBidNotifications(boolean bidNotifications) {
        this.bidNotifications = bidNotifications;
    }

    public boolean isSoundEffects() {
        return soundEffects;
    }

    public void setSoundEffects(boolean soundEffects) {
        this.soundEffects = soundEffects;
    }

    public boolean isLoginNotifications() {
        return loginNotifications;
    }

    public void setLoginNotifications(boolean loginNotifications) {
        this.loginNotifications = loginNotifications;
    }

    public boolean isFavoriteNotifications() {
        return favoriteNotifications;
    }

    public void setFavoriteNotifications(boolean favoriteNotifications) {
        this.favoriteNotifications = favoriteNotifications;
    }

    /**
     * Toggles the specified setting and returns the new value.
     */
    public boolean toggle(String settingKey) {
        return switch (settingKey) {
            case "sale" -> {
                saleNotifications = !saleNotifications;
                yield saleNotifications;
            }
            case "bid" -> {
                bidNotifications = !bidNotifications;
                yield bidNotifications;
            }
            case "sound" -> {
                soundEffects = !soundEffects;
                yield soundEffects;
            }
            case "login" -> {
                loginNotifications = !loginNotifications;
                yield loginNotifications;
            }
            case "favorite" -> {
                favoriteNotifications = !favoriteNotifications;
                yield favoriteNotifications;
            }
            default -> false;
        };
    }

    /**
     * Gets the value of a setting by key.
     */
    public boolean get(String settingKey) {
        return switch (settingKey) {
            case "sale" -> saleNotifications;
            case "bid" -> bidNotifications;
            case "sound" -> soundEffects;
            case "login" -> loginNotifications;
            case "favorite" -> favoriteNotifications;
            default -> true;
        };
    }
}
