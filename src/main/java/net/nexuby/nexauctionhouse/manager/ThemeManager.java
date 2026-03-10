package net.nexuby.nexauctionhouse.manager;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ThemeManager {

    private final NexAuctionHouse plugin;
    private final Map<String, FileConfiguration> themes = new LinkedHashMap<>();
    private final Map<UUID, String> playerThemes = new ConcurrentHashMap<>();

    public ThemeManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    public void load() {
        themes.clear();

        File themesFolder = new File(plugin.getDataFolder(), "themes");
        if (!themesFolder.exists()) {
            themesFolder.mkdirs();
        }

        // Save default theme files
        saveDefault("default.yml");
        saveDefault("dark.yml");
        saveDefault("nether.yml");

        // Load all theme files from the folder
        File[] files = themesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String id = file.getName().replace(".yml", "").toLowerCase();
                themes.put(id, YamlConfiguration.loadConfiguration(file));
            }
        }

        plugin.getLogger().info("Loaded " + themes.size() + " GUI themes.");
    }

    private void saveDefault(String fileName) {
        File file = new File(plugin.getDataFolder(), "themes/" + fileName);
        if (!file.exists()) {
            plugin.saveResource("themes/" + fileName, false);
        }
    }

    // -- Player theme management --

    public String getPlayerTheme(UUID uuid) {
        return playerThemes.getOrDefault(uuid, "default");
    }

    public void setPlayerTheme(UUID uuid, String theme) {
        playerThemes.put(uuid, theme);
    }

    public void loadPlayerTheme(UUID uuid) {
        String theme = plugin.getAuctionManager().getDao().getPlayerTheme(uuid);
        if (theme != null) {
            playerThemes.put(uuid, theme);
        }
    }

    public void savePlayerTheme(UUID uuid, String theme) {
        playerThemes.put(uuid, theme);
        plugin.getAuctionManager().getDao().setPlayerTheme(uuid, theme);
    }

    public void unloadPlayerTheme(UUID uuid) {
        playerThemes.remove(uuid);
    }

    // -- Theme data access --

    public FileConfiguration getTheme(String id) {
        return themes.get(id.toLowerCase());
    }

    public Set<String> getThemeIds() {
        return themes.keySet();
    }

    public String getThemeName(String id) {
        FileConfiguration cfg = themes.get(id.toLowerCase());
        if (cfg == null) return id;
        return cfg.getString("name", id);
    }

    public String getThemeDescription(String id) {
        FileConfiguration cfg = themes.get(id.toLowerCase());
        if (cfg == null) return "";
        return cfg.getString("description", "");
    }

    public Material getThemeIcon(String id) {
        FileConfiguration cfg = themes.get(id.toLowerCase());
        if (cfg == null) return Material.PAINTING;
        Material mat = Material.matchMaterial(cfg.getString("icon", "PAINTING"));
        return mat != null ? mat : Material.PAINTING;
    }

    public boolean isValidTheme(String id) {
        return themes.containsKey(id.toLowerCase());
    }

    // -- Theme resolution helpers --

    public Material getFillerMaterial(UUID playerUuid) {
        String themeId = getPlayerTheme(playerUuid);
        FileConfiguration cfg = themes.get(themeId);
        if (cfg == null) cfg = themes.get("default");
        if (cfg == null) return Material.BLACK_STAINED_GLASS_PANE;

        Material mat = Material.matchMaterial(cfg.getString("filler.material", "BLACK_STAINED_GLASS_PANE"));
        return mat != null ? mat : Material.BLACK_STAINED_GLASS_PANE;
    }

    public String getTitleColor(UUID playerUuid) {
        String themeId = getPlayerTheme(playerUuid);
        FileConfiguration cfg = themes.get(themeId);
        if (cfg == null) cfg = themes.get("default");
        if (cfg == null) return "<dark_gray>";
        return cfg.getString("title-color", "<dark_gray>");
    }

    public Material getButtonMaterial(UUID playerUuid, String buttonKey) {
        String themeId = getPlayerTheme(playerUuid);
        FileConfiguration cfg = themes.get(themeId);
        if (cfg == null) cfg = themes.get("default");
        if (cfg == null) return null;

        String materialName = cfg.getString("buttons." + buttonKey);
        if (materialName == null) return null;
        return Material.matchMaterial(materialName);
    }
}
