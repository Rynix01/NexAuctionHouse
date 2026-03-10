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

    // Slot assignments
    private static final int ITEM_SLOT = 13;
    private static final int ENCHANT_SLOT = 29;
    private static final int ATTRIBUTE_SLOT = 31;
    private static final int CUSTOM_SLOT = 33;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    // Shulker content slots (3 rows of 9)
    private static final int[] SHULKER_SLOTS = {
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
            46, 47, 48, 49, 50, 51, 52
    };

    // Book page navigation
    private static final int BOOK_SLOT = 22;
    private static final int BOOK_PREV_SLOT = 29;
    private static final int BOOK_NEXT_SLOT = 33;
    private int bookPage = 0;
    private List<Component> bookPages;

    // Armor slots
    private static final int ARMOR_HELMET_SLOT = 20;
    private static final int ARMOR_CHEST_SLOT = 29;
    private static final int ARMOR_LEGS_SLOT = 38;
    private static final int ARMOR_BOOTS_SLOT = 47;

    public PreviewGui(NexAuctionHouse plugin, Player viewer, ItemStack item, Runnable backAction) {
        super(plugin, viewer);
        this.previewItem = item.clone();
        this.backAction = backAction;
    }

    @Override
    protected void build() {
        inventory = Bukkit.createInventory(this, 54, text("<dark_gray>Item Preview"));

        // Fill background
        ItemStack filler = createThemedFiller();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Main item display (slot 13)
        inventory.setItem(ITEM_SLOT, previewItem.clone());

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

        // Back button
        ItemStack back = new ItemStack(Material.DARK_OAK_DOOR);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(text("<yellow>Back"));
        backMeta.lore(List.of(text("<gray>Return to the previous menu.")));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(text("<red>Close"));
        closeMeta.lore(List.of(text("<gray>Close the menu.")));
        close.setItemMeta(closeMeta);
        inventory.setItem(CLOSE_SLOT, close);
    }

    // -- Standard Preview (enchants, attributes, custom item info) --

    private void buildStandardPreview() {
        // Enchantments panel
        inventory.setItem(ENCHANT_SLOT, buildEnchantmentPanel());

        // Attributes panel
        inventory.setItem(ATTRIBUTE_SLOT, buildAttributePanel());

        // Custom item info panel
        inventory.setItem(CUSTOM_SLOT, buildCustomItemPanel());
    }

    private ItemStack buildEnchantmentPanel() {
        ItemStack panel = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = panel.getItemMeta();
        meta.displayName(text("<aqua>Enchantments"));

        List<Component> lore = new ArrayList<>();
        lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));

        Map<Enchantment, Integer> enchants = previewItem.getEnchantments();
        if (enchants.isEmpty()) {
            // Check stored enchantments (for enchanted books)
            if (previewItem.getItemMeta() instanceof EnchantmentStorageMeta storageMeta) {
                enchants = storageMeta.getStoredEnchants();
            }
        }

        if (enchants.isEmpty()) {
            lore.add(text("<gray>No enchantments."));
        } else {
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                String name = formatEnchantmentName(entry.getKey());
                int level = entry.getValue();
                String levelStr = toRoman(level);
                boolean maxed = level >= entry.getKey().getMaxLevel();
                String color = maxed ? "<gold>" : "<green>";
                lore.add(text(color + name + " " + levelStr));
            }
        }

        lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
        meta.lore(lore);
        panel.setItemMeta(meta);
        return panel;
    }

    private ItemStack buildAttributePanel() {
        ItemStack panel = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = panel.getItemMeta();
        meta.displayName(text("<yellow>Attributes"));

        List<Component> lore = new ArrayList<>();
        lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));

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

                    lore.add(text("<green>" + formatted + " <gray>" + attrName));
                }
            }
        }

        if (!hasAttributes) {
            lore.add(text("<gray>No custom attributes."));
        }

        lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
        meta.lore(lore);
        panel.setItemMeta(meta);
        return panel;
    }

    private ItemStack buildCustomItemPanel() {
        ItemStack panel = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = panel.getItemMeta();
        meta.displayName(text("<light_purple>Item Details"));

        List<Component> lore = new ArrayList<>();
        lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));

        // Material type
        lore.add(text("<gray>Type: <white>" + formatMaterialName(previewItem.getType().name())));

        // Stack size
        if (previewItem.getAmount() > 1) {
            lore.add(text("<gray>Amount: <white>" + previewItem.getAmount()));
        }

        // Durability
        if (previewItem.getItemMeta() instanceof Damageable damageable && damageable.hasDamage()) {
            int maxDurability = previewItem.getType().getMaxDurability();
            int remaining = maxDurability - damageable.getDamage();
            lore.add(text("<gray>Durability: <white>" + remaining + "/" + maxDurability));
        }

        // Unbreakable
        ItemMeta itemMeta = previewItem.getItemMeta();
        if (itemMeta != null && itemMeta.isUnbreakable()) {
            lore.add(text("<aqua>Unbreakable"));
        }

        // Custom model data
        if (itemMeta != null && itemMeta.hasCustomModelData()) {
            lore.add(text("<gray>Custom Model: <white>#" + itemMeta.getCustomModelData()));
        }

        // Custom item hook info
        if (plugin.getItemHookManager() != null) {
            String customId = plugin.getItemHookManager().getCustomItemId(previewItem);
            if (customId != null) {
                lore.add(text("<gray>Plugin Item: <yellow>" + customId));
            }
        }

        // Average market price
        double avg = plugin.getAuctionManager().getAveragePrice(previewItem.getType().name());
        if (avg > 0) {
            lore.add(text("<gray>Avg Market Price: <aqua>" + plugin.getEconomyManager().format(avg)));
        }

        lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
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
        ItemStack label = new ItemStack(Material.CHEST);
        ItemMeta labelMeta = label.getItemMeta();
        labelMeta.displayName(text("<gold>Shulker Box Contents"));
        labelMeta.lore(List.of(
                text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"),
                text("<gray>Contents of the shulker box."),
                text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━")
        ));
        label.setItemMeta(labelMeta);
        inventory.setItem(22, label);

        // Display shulker contents
        ItemStack[] contents = shulker.getInventory().getContents();
        int slotIdx = 0;
        for (ItemStack content : contents) {
            if (slotIdx >= SHULKER_SLOTS.length) break;
            if (content != null && content.getType() != Material.AIR) {
                inventory.setItem(SHULKER_SLOTS[slotIdx], content.clone());
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
        ItemStack bookDisplay = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta bookDisplayMeta = bookDisplay.getItemMeta();
        bookDisplayMeta.displayName(text("<gold>Page " + (safeIndex + 1) + "/" + bookPages.size()));

        List<Component> lore = new ArrayList<>();
        lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));

        // Convert page content to plain text and wrap lines
        String plainText = PlainTextComponentSerializer.plainText().serialize(pageContent);
        for (String line : wrapText(plainText, 40)) {
            lore.add(text("<white>" + line));
        }

        lore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
        bookDisplayMeta.lore(lore);
        bookDisplay.setItemMeta(bookDisplayMeta);
        inventory.setItem(BOOK_SLOT, bookDisplay);

        // Previous page button
        if (safeIndex > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.displayName(text("<yellow>Previous Page"));
            prev.setItemMeta(prevMeta);
            inventory.setItem(BOOK_PREV_SLOT, prev);
        }

        // Next page button
        if (safeIndex < bookPages.size() - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.displayName(text("<yellow>Next Page"));
            next.setItemMeta(nextMeta);
            inventory.setItem(BOOK_NEXT_SLOT, next);
        }

        // Book info
        BookMeta bookMeta = (BookMeta) previewItem.getItemMeta();
        ItemStack info = new ItemStack(Material.NAME_TAG);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(text("<light_purple>Book Details"));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
        if (bookMeta.hasTitle()) {
            infoLore.add(text("<gray>Title: <white>" + bookMeta.getTitle()));
        }
        if (bookMeta.hasAuthor()) {
            infoLore.add(text("<gray>Author: <white>" + bookMeta.getAuthor()));
        }
        infoLore.add(text("<gray>Pages: <white>" + bookPages.size()));
        infoLore.add(text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"));
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(40, info);
    }

    // -- Armor Preview --

    private void buildArmorPreview() {
        // Show the item's own stats
        buildStandardPreview();

        // Show armor set visualization
        ItemStack label = new ItemStack(Material.ARMOR_STAND);
        ItemMeta labelMeta = label.getItemMeta();
        labelMeta.displayName(text("<gold>Armor Piece"));
        labelMeta.lore(List.of(
                text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━"),
                text("<gray>This is an armor piece."),
                text("<gray>Slot: <white>" + getArmorSlotName(previewItem.getType())),
                text("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━")
        ));
        label.setItemMeta(labelMeta);
        inventory.setItem(22, label);

        // Place the armor in its visual slot position
        int armorVisSlot = getArmorDisplaySlot(previewItem.getType());
        if (armorVisSlot >= 0) {
            inventory.setItem(armorVisSlot, previewItem.clone());
        }

        // Show empty slots for other armor pieces as gray glass
        ItemStack empty = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta emptyMeta = empty.getItemMeta();
        emptyMeta.displayName(text("<gray>Empty Slot"));
        empty.setItemMeta(emptyMeta);

        int[] armorSlots = {ARMOR_HELMET_SLOT, ARMOR_CHEST_SLOT, ARMOR_LEGS_SLOT, ARMOR_BOOTS_SLOT};
        String[] slotLabels = {"Helmet", "Chestplate", "Leggings", "Boots"};
        for (int i = 0; i < armorSlots.length; i++) {
            if (armorSlots[i] != armorVisSlot) {
                ItemStack placeholder = empty.clone();
                ItemMeta phMeta = placeholder.getItemMeta();
                phMeta.displayName(text("<gray>" + slotLabels[i]));
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

        if (slot == BACK_SLOT) {
            if (backAction != null) {
                backAction.run();
            } else {
                viewer.closeInventory();
            }
            return;
        }

        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }

        // Book page navigation
        if (bookPages != null && !bookPages.isEmpty()) {
            if (slot == BOOK_PREV_SLOT && bookPage > 0) {
                bookPage--;
                rebuildBookPage();
            } else if (slot == BOOK_NEXT_SLOT && bookPage < bookPages.size() - 1) {
                bookPage++;
                rebuildBookPage();
            }
        }
    }

    private void rebuildBookPage() {
        // Clear book area
        ItemStack filler = createThemedFiller();

        inventory.setItem(BOOK_SLOT, filler);
        inventory.setItem(BOOK_PREV_SLOT, filler);
        inventory.setItem(BOOK_NEXT_SLOT, filler);

        renderBookPage();
    }

    // -- Utility Methods --

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
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) return ARMOR_HELMET_SLOT;
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) return ARMOR_CHEST_SLOT;
        if (name.endsWith("_LEGGINGS")) return ARMOR_LEGS_SLOT;
        if (name.endsWith("_BOOTS")) return ARMOR_BOOTS_SLOT;
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
