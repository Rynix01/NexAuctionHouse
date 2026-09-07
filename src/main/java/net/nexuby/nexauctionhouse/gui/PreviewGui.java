package net.nexuby.nexauctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;

import java.util.*;

/**
 * Preview GUI for detailed item inspection. Shows full enchantments,
 * attributes, shulker box contents, book pages, and custom item info.
 */
public class PreviewGui extends AbstractGui {

    private final ItemStack previewItem;
    private final Runnable backAction;

    private FileConfiguration gui;
    private int itemSlot;
    private int enchantSlot;
    private int attributeSlot;
    private int customSlot;
    private int backSlot;
    private int closeSlot;
    private List<Integer> shulkerSlots;

    // Book page navigation
    private int bookSlot;
    private int bookPrevSlot;
    private int bookNextSlot;
    private int bookPage = 0;
    private List<Component> bookPages;

    // Armor slots
    private int armorHelmetSlot;
    private int armorChestSlot;
    private int armorLegsSlot;
    private int armorBootsSlot;

    public PreviewGui(NexAuctionHouse plugin, Player viewer, ItemStack item, Runnable backAction) {
        super(plugin, viewer);
        this.previewItem = item.clone();
        this.backAction = backAction;
    }

    @Override
    protected void build() {
        gui = plugin.getGuiConfig().getGui("preview");
        if (gui == null) {
            plugin.getLogger().warning("GUI config 'preview' not found!");
            return;
        }
        loadLayout();
        inventory = Bukkit.createInventory(this, gui.getInt("size", 54),
                text(gui.getString("title", "<dark_gray>Item Preview")));
        applyFiller(gui);

        inventory.setItem(itemSlot, previewItem.clone());

        // Decide layout based on item type
        if (isShulkerBox(previewItem)) {
            buildShulkerPreview();
        } else if (isWrittenBook(previewItem)) {
            buildBookPreview();
        } else if (isArmorPiece(previewItem)) {
            buildArmorPreview();
        } else {
            buildStandardPreview();
        }

        ConfigurationSection buttons = gui.getConfigurationSection("buttons");
        if (buttons != null) {
            if (backSlot >= 0) inventory.setItem(backSlot, createButton(buttons.getConfigurationSection("back")));
            if (closeSlot >= 0) inventory.setItem(closeSlot, createButton(buttons.getConfigurationSection("close")));
        }
    }

    private void loadLayout() {
        itemSlot = gui.getInt("slots.item", 13);
        enchantSlot = gui.getInt("slots.enchantments", 29);
        attributeSlot = gui.getInt("slots.attributes", 31);
        customSlot = gui.getInt("slots.details", 33);
        bookSlot = gui.getInt("slots.book-page", 22);
        bookPrevSlot = gui.getInt("slots.book-previous", 29);
        bookNextSlot = gui.getInt("slots.book-next", 33);
        armorHelmetSlot = gui.getInt("slots.armor-helmet", 20);
        armorChestSlot = gui.getInt("slots.armor-chestplate", 29);
        armorLegsSlot = gui.getInt("slots.armor-leggings", 38);
        armorBootsSlot = gui.getInt("slots.armor-boots", 47);
        shulkerSlots = gui.getIntegerList("shulker-item-slots");
        backSlot = gui.getInt("buttons.back.slot", 45);
        closeSlot = gui.getInt("buttons.close.slot", 53);
    }

    // -- Standard Preview (enchants, attributes, custom item info) --

    private void buildStandardPreview() {
        // Enchantments panel
        inventory.setItem(enchantSlot, buildEnchantmentPanel());

        // Attributes panel
        inventory.setItem(attributeSlot, buildAttributePanel());

        // Custom item info panel
        inventory.setItem(customSlot, buildCustomItemPanel());
    }

    private ItemStack buildEnchantmentPanel() {
        ConfigurationSection section = gui.getConfigurationSection("panels.enchantments");
        ItemStack panel = panelItem(section, Material.ENCHANTED_BOOK);
        ItemMeta meta = panel.getItemMeta();
        meta.displayName(text(value(section, "name", "<aqua>Enchantments")));

        List<Component> lore = new ArrayList<>();
        lore.add(text(value(section, "separator", "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━")));

        Map<Enchantment, Integer> enchants = previewItem.getEnchantments();
        if (enchants.isEmpty()) {
            // Check stored enchantments (for enchanted books)
            if (previewItem.getItemMeta() instanceof EnchantmentStorageMeta storageMeta) {
                enchants = storageMeta.getStoredEnchants();
            }
        }

        if (enchants.isEmpty()) {
            lore.add(text(value(section, "empty", "<gray>No enchantments.")));
        } else {
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                String name = formatEnchantmentName(entry.getKey());
                int level = entry.getValue();
                String levelStr = toRoman(level);
                boolean maxed = level >= entry.getKey().getMaxLevel();
                String color = maxed ? "<gold>" : "<green>";
                lore.add(text(replace(value(section, "entry", "{color}{name} {level}"),
                        "{color}", color, "{name}", name, "{level}", levelStr)));
            }
        }

        lore.add(text(value(section, "separator", "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━")));
        meta.lore(lore);
        panel.setItemMeta(meta);
        return panel;
    }

    private ItemStack buildAttributePanel() {
        ConfigurationSection section = gui.getConfigurationSection("panels.attributes");
        ItemStack panel = panelItem(section, Material.IRON_SWORD);
        ItemMeta meta = panel.getItemMeta();
        meta.displayName(text(value(section, "name", "<yellow>Attributes")));

        List<Component> lore = new ArrayList<>();
        lore.add(text(value(section, "separator", "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━")));

        ItemMeta itemMeta = previewItem.getItemMeta();
        boolean hasAttributes = false;

        if (itemMeta != null && itemMeta.hasAttributeModifiers()) {
            for (Attribute attr : Registry.ATTRIBUTE) {
                Collection<AttributeModifier> modifiers = itemMeta.getAttributeModifiers(attr);
                if (modifiers == null || modifiers.isEmpty()) continue;

                for (AttributeModifier mod : modifiers) {
                    hasAttributes = true;
                    String attrName = formatAttributeName(attr.key().value());
                    double amount = mod.getAmount();
                    String sign = amount >= 0 ? "+" : "";
                    String formatted;

                    if (mod.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                        formatted = sign + String.format("%.1f", amount);
                    } else {
                        formatted = sign + String.format("%.0f%%", amount * 100);
                    }

                    lore.add(text(replace(value(section, "entry", "<green>{amount} <gray>{attribute}"),
                            "{amount}", formatted, "{attribute}", attrName)));
                }
            }
        }

        if (!hasAttributes) {
            lore.add(text(value(section, "empty", "<gray>No custom attributes.")));
        }

        lore.add(text(value(section, "separator", "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━")));
        meta.lore(lore);
        panel.setItemMeta(meta);
        return panel;
    }

    private ItemStack buildCustomItemPanel() {
        ConfigurationSection section = gui.getConfigurationSection("panels.details");
        ItemStack panel = panelItem(section, Material.NAME_TAG);
        ItemMeta meta = panel.getItemMeta();
        meta.displayName(text(value(section, "name", "<light_purple>Item Details")));

        List<Component> lore = new ArrayList<>();
        lore.add(text(value(section, "separator", "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━")));

        // Material type
        lore.add(text(replace(value(section, "type", "<gray>Type: <white>{type}"),
                "{type}", formatMaterialName(previewItem.getType().name()))));

        // Stack size
        if (previewItem.getAmount() > 1) {
            lore.add(text(replace(value(section, "amount", "<gray>Amount: <white>{amount}"),
                    "{amount}", String.valueOf(previewItem.getAmount()))));
        }

        // Durability
        if (previewItem.getItemMeta() instanceof Damageable damageable && damageable.hasDamage()) {
            int maxDurability = previewItem.getType().getMaxDurability();
            int remaining = maxDurability - damageable.getDamage();
            lore.add(text(replace(value(section, "durability", "<gray>Durability: <white>{remaining}/{max}"),
                    "{remaining}", String.valueOf(remaining), "{max}", String.valueOf(maxDurability))));
        }

        // Unbreakable
        ItemMeta itemMeta = previewItem.getItemMeta();
        if (itemMeta != null && itemMeta.isUnbreakable()) {
            lore.add(text(value(section, "unbreakable", "<aqua>Unbreakable")));
        }

        // Custom model data
        if (itemMeta != null && itemMeta.hasCustomModelData()) {
            lore.add(text(replace(value(section, "custom-model", "<gray>Custom Model: <white>#{model}"),
                    "{model}", String.valueOf(itemMeta.getCustomModelData()))));
        }

        // Custom item hook info
        if (plugin.getItemHookManager() != null) {
            String customId = plugin.getItemHookManager().getCustomItemId(previewItem);
            if (customId != null) {
                lore.add(text(replace(value(section, "plugin-item", "<gray>Plugin Item: <yellow>{id}"),
                        "{id}", escapeMiniMessage(customId))));
            }
        }

        // Average market price
        double avg = plugin.getAuctionManager().getAveragePrice(previewItem.getType().name());
        if (avg > 0) {
            lore.add(text(replace(value(section, "average-price", "<gray>Avg Market Price: <aqua>{price}"),
                    "{price}", plugin.getEconomyManager().format(avg))));
            lore.add(text(replace(value(section, "average-source",
                            "<dark_gray>Completed sales, last {days} days"),
                    "{days}", String.valueOf(plugin.getConfigManager().getAveragePriceWindowDays()))));
        }

        lore.add(text(value(section, "separator", "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━")));
        meta.lore(lore);
        panel.setItemMeta(meta);
        return panel;
    }

    // -- Shulker Box Preview --

    private void buildShulkerPreview() {
        ItemMeta itemMeta = previewItem.getItemMeta();
        if (!(itemMeta instanceof BlockStateMeta blockMeta)) {
            buildStandardPreview();
            return;
        }

        if (!(blockMeta.getBlockState() instanceof ShulkerBox shulker)) {
            buildStandardPreview();
            return;
        }

        // Label
        ConfigurationSection shulkerPanel = gui.getConfigurationSection("panels.shulker");
        ItemStack label = panelItem(shulkerPanel, Material.CHEST);
        ItemMeta labelMeta = label.getItemMeta();
        labelMeta.displayName(text(value(shulkerPanel, "name", "<gold>Shulker Box Contents")));
        labelMeta.lore(configuredLore(shulkerPanel, "lore"));
        label.setItemMeta(labelMeta);
        inventory.setItem(gui.getInt("slots.shulker-info", 22), label);

        // Display shulker contents
        ItemStack[] contents = shulker.getInventory().getContents();
        int slotIdx = 0;
        for (ItemStack content : contents) {
            if (slotIdx >= shulkerSlots.size()) break;
            if (content != null && content.getType() != Material.AIR) {
                inventory.setItem(shulkerSlots.get(slotIdx), content.clone());
            }
            slotIdx++;
        }
    }

    // -- Written Book Preview --

    private void buildBookPreview() {
        if (!(previewItem.getItemMeta() instanceof BookMeta bookMeta)) {
            buildStandardPreview();
            return;
        }

        bookPages = bookMeta.pages();
        if (bookPages.isEmpty()) {
            buildStandardPreview();
            return;
        }

        renderBookPage();
    }

    private void renderBookPage() {
        if (bookPages == null || bookPages.isEmpty()) return;

        int safeIndex = Math.min(bookPage, bookPages.size() - 1);
        Component pageContent = bookPages.get(safeIndex);

        // Book display
        ConfigurationSection bookPanel = gui.getConfigurationSection("panels.book-page");
        ItemStack bookDisplay = panelItem(bookPanel, Material.WRITABLE_BOOK);
        ItemMeta bookDisplayMeta = bookDisplay.getItemMeta();
        bookDisplayMeta.displayName(text(replace(value(bookPanel, "name", "<gold>Page {page}/{pages}"),
                "{page}", String.valueOf(safeIndex + 1), "{pages}", String.valueOf(bookPages.size()))));

        List<Component> lore = new ArrayList<>();
        lore.add(text(value(bookPanel, "separator", "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━")));

        // Convert page content to plain text and wrap lines
        String plainText = PlainTextComponentSerializer.plainText().serialize(pageContent);
        for (String line : wrapText(plainText, 40)) {
            lore.add(text("<white>" + line));
        }

        lore.add(text(value(bookPanel, "separator", "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━")));
        bookDisplayMeta.lore(lore);
        bookDisplay.setItemMeta(bookDisplayMeta);
        inventory.setItem(bookSlot, bookDisplay);

        // Previous page button
        if (safeIndex > 0) {
            inventory.setItem(bookPrevSlot, createButton(
                    gui.getConfigurationSection("buttons.book-previous")));
        }

        // Next page button
        if (safeIndex < bookPages.size() - 1) {
            inventory.setItem(bookNextSlot, createButton(
                    gui.getConfigurationSection("buttons.book-next")));
        }

        // Book info
        BookMeta bookMeta = (BookMeta) previewItem.getItemMeta();
        ConfigurationSection detailsPanel = gui.getConfigurationSection("panels.book-details");
        ItemStack info = panelItem(detailsPanel, Material.NAME_TAG);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(text(value(detailsPanel, "name", "<light_purple>Book Details")));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(text(value(detailsPanel, "separator", "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━")));
        if (bookMeta.hasTitle()) {
            infoLore.add(text(replace(value(detailsPanel, "title", "<gray>Title: <white>{title}"),
                    "{title}", escapeMiniMessage(bookMeta.getTitle()))));
        }
        if (bookMeta.hasAuthor()) {
            infoLore.add(text(replace(value(detailsPanel, "author", "<gray>Author: <white>{author}"),
                    "{author}", escapeMiniMessage(bookMeta.getAuthor()))));
        }
        infoLore.add(text(replace(value(detailsPanel, "pages", "<gray>Pages: <white>{pages}"),
                "{pages}", String.valueOf(bookPages.size()))));
        infoLore.add(text(value(detailsPanel, "separator", "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━")));
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(gui.getInt("slots.book-details", 40), info);
    }

    // -- Armor Preview --

    private void buildArmorPreview() {
        // Show the item's own stats
        buildStandardPreview();

        // Show armor set visualization
        ConfigurationSection armorPanel = gui.getConfigurationSection("panels.armor");
        ItemStack label = panelItem(armorPanel, Material.ARMOR_STAND);
        ItemMeta labelMeta = label.getItemMeta();
        labelMeta.displayName(text(value(armorPanel, "name", "<gold>Armor Piece")));
        List<Component> armorLore = new ArrayList<>();
        for (String line : armorPanel != null ? armorPanel.getStringList("lore") : List.<String>of()) {
            armorLore.add(text(replace(line, "{slot}", getArmorSlotName(previewItem.getType()))));
        }
        labelMeta.lore(armorLore);
        label.setItemMeta(labelMeta);
        inventory.setItem(gui.getInt("slots.armor-info", 22), label);

        // Place the armor in its visual slot position
        int armorVisSlot = getArmorDisplaySlot(previewItem.getType());
        if (armorVisSlot >= 0) {
            inventory.setItem(armorVisSlot, previewItem.clone());
        }

        // Show empty slots for other armor pieces as gray glass
        ConfigurationSection emptyArmor = gui.getConfigurationSection("panels.empty-armor");
        ItemStack empty = panelItem(emptyArmor, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta emptyMeta = empty.getItemMeta();
        emptyMeta.displayName(text(value(emptyArmor, "name", "<gray>{slot}")));
        empty.setItemMeta(emptyMeta);

        int[] armorSlots = {armorHelmetSlot, armorChestSlot, armorLegsSlot, armorBootsSlot};
        String[] slotLabels = {"Helmet", "Chestplate", "Leggings", "Boots"};
        for (int i = 0; i < armorSlots.length; i++) {
            if (armorSlots[i] != armorVisSlot) {
                ItemStack placeholder = empty.clone();
                ItemMeta phMeta = placeholder.getItemMeta();
                phMeta.displayName(text(replace(value(emptyArmor, "name", "<gray>{slot}"),
                        "{slot}", slotLabels[i])));
                placeholder.setItemMeta(phMeta);
                inventory.setItem(armorSlots[i], placeholder);
            }
        }
    }

    // -- Click Handling --

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == backSlot) {
            if (backAction != null) {
                backAction.run();
            } else {
                viewer.closeInventory();
            }
            return;
        }

        if (slot == closeSlot) {
            viewer.closeInventory();
            return;
        }

        // Book page navigation
        if (bookPages != null && !bookPages.isEmpty()) {
            if (slot == bookPrevSlot && bookPage > 0) {
                bookPage--;
                rebuildBookPage();
            } else if (slot == bookNextSlot && bookPage < bookPages.size() - 1) {
                bookPage++;
                rebuildBookPage();
            }
        }
    }

    private void rebuildBookPage() {
        // Clear book area
        ItemStack filler = createThemedFiller();

        inventory.setItem(bookSlot, filler);
        inventory.setItem(bookPrevSlot, filler);
        inventory.setItem(bookNextSlot, filler);

        renderBookPage();
    }

    // -- Utility Methods --

    private ItemStack panelItem(ConfigurationSection section, Material fallback) {
        Material material = fallback;
        if (section != null) {
            Material configured = Material.matchMaterial(section.getString("material", fallback.name()));
            if (configured != null) material = configured;
        }
        return new ItemStack(material);
    }

    private static String value(ConfigurationSection section, String key, String fallback) {
        return section == null ? fallback : section.getString(key, fallback);
    }

    private List<Component> configuredLore(ConfigurationSection section, String key) {
        if (section == null) return List.of();
        List<Component> lore = new ArrayList<>();
        for (String line : section.getStringList(key)) lore.add(text(line));
        return lore;
    }

    private static String replace(String value, String... replacements) {
        String result = value;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace(replacements[i], replacements[i + 1]);
        }
        return result;
    }

    private boolean isShulkerBox(ItemStack item) {
        return item.getType().name().contains("SHULKER_BOX");
    }

    private boolean isWrittenBook(ItemStack item) {
        return item.getType() == Material.WRITTEN_BOOK;
    }

    private boolean isArmorPiece(ItemStack item) {
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.equals("TURTLE_HELMET") || name.equals("ELYTRA");
    }

    private String getArmorSlotName(Material mat) {
        String name = mat.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) return "Helmet";
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) return "Chestplate";
        if (name.endsWith("_LEGGINGS")) return "Leggings";
        if (name.endsWith("_BOOTS")) return "Boots";
        return "Unknown";
    }

    private int getArmorDisplaySlot(Material mat) {
        String name = mat.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) return armorHelmetSlot;
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) return armorChestSlot;
        if (name.endsWith("_LEGGINGS")) return armorLegsSlot;
        if (name.endsWith("_BOOTS")) return armorBootsSlot;
        return -1;
    }

    private String formatEnchantmentName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        StringBuilder sb = new StringBuilder();
        for (String word : key.split("_")) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private String formatAttributeName(String name) {
        // GENERIC_ATTACK_DAMAGE -> Attack Damage
        String clean = name.replace("GENERIC_", "").replace("PLAYER_", "");
        StringBuilder sb = new StringBuilder();
        for (String word : clean.split("_")) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private String formatMaterialName(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("_")) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        // Split by existing newlines first
        for (String paragraph : text.split("\n")) {
            if (paragraph.length() <= maxWidth) {
                lines.add(paragraph);
                continue;
            }
            // Word-wrap long lines
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (line.length() + word.length() + 1 > maxWidth && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder();
                }
                if (!line.isEmpty()) line.append(" ");
                line.append(word);
            }
            if (!line.isEmpty()) lines.add(line.toString());
        }
        // Limit to 10 lines to keep lore reasonable
        if (lines.size() > 10) {
            lines = new ArrayList<>(lines.subList(0, 10));
            lines.add("...");
        }
        return lines;
    }
}
