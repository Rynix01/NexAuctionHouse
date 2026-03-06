package net.nexuby.nexauctionhouse.config;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class GuiConfig {

    private final NexAuctionHouse plugin;
    private final Map<String, FileConfiguration> guiConfigs = new HashMap<>();

    public GuiConfig(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    public void load() {
        guiConfigs.clear();

        File guiFolder = new File(plugin.getDataFolder(), "gui");
        if (!guiFolder.exists()) {
            guiFolder.mkdirs();
        }

        // Save default GUI configs
        saveDefault("main-menu.yml");
        saveDefault("confirm.yml");
        saveDefault("expired.yml");
        saveDefault("categories.yml");
        saveDefault("bid.yml");
        saveDefault("favorites.yml");

        // Load all GUI configs from the folder
        File[] files = guiFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName().replace(".yml", "");
                guiConfigs.put(name, YamlConfiguration.loadConfiguration(file));
            }
        }

        plugin.getLogger().info("Loaded " + guiConfigs.size() + " GUI configurations.");
    }

    private void saveDefault(String fileName) {
        File file = new File(plugin.getDataFolder(), "gui/" + fileName);
        if (!file.exists()) {
            plugin.saveResource("gui/" + fileName, false);
        }
    }

    /**
     * Gets a specific GUI config by name (without .yml extension).
     */
    public FileConfiguration getGui(String name) {
        return guiConfigs.get(name);
    }

    public String getTitle(String guiName) {
        FileConfiguration cfg = guiConfigs.get(guiName);
        if (cfg == null) return guiName;
        return cfg.getString("title", guiName);
    }

    public int getSize(String guiName) {
        FileConfiguration cfg = guiConfigs.get(guiName);
        if (cfg == null) return 54;
        return cfg.getInt("size", 54);
    }
}
