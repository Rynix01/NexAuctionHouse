package net.nexuby.nexauctionhouse.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LangManager {

    private final NexAuctionHouse plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration langConfig;
    private final Map<String, String> messageCache = new HashMap<>();

    public LangManager(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    public void load() {
        messageCache.clear();

        String language = plugin.getConfigManager().getDefaultLanguage();
        File langFolder = new File(plugin.getDataFolder(), "lang");

        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // Save default language files from resources
        saveDefaultLang("en");
        saveDefaultLang("tr");

        File langFile = new File(langFolder, language + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file '" + language + ".yml' not found, falling back to 'en.yml'");
            langFile = new File(langFolder, "en.yml");
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        // Cache all messages for quick access
        for (String key : langConfig.getKeys(true)) {
            if (langConfig.isString(key)) {
                messageCache.put(key, langConfig.getString(key));
            }
        }

        plugin.getLogger().info("Loaded language: " + language + " (" + messageCache.size() + " messages)");
    }

    private void saveDefaultLang(String lang) {
        File langFile = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang/" + lang + ".yml", false);
        }
    }

    /**
     * Gets a raw message string from the language file.
     */
    public String getRaw(String key) {
        return messageCache.getOrDefault(key, "<red>Missing message: " + key);
    }

    /**
     * Gets a raw message string with placeholder replacements.
     */
    public String getRaw(String key, String... replacements) {
        String message = getRaw(key);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }

    /**
     * Parses the message through MiniMessage and returns a Component.
     */
    public Component get(String key) {
        return miniMessage.deserialize(getRaw(key));
    }

    /**
     * Parses the message through MiniMessage with placeholder replacements.
     */
    public Component get(String key, String... replacements) {
        return miniMessage.deserialize(getRaw(key, replacements));
    }

    /**
     * Gets the prefix component used in chat messages.
     */
    public Component getPrefix() {
        return get("prefix");
    }

    /**
     * Builds a prefixed message component.
     */
    public Component prefixed(String key, String... replacements) {
        return getPrefix().append(get(key, replacements));
    }
}
