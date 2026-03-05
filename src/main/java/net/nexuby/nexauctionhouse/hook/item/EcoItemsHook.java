package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hook for EcoItems (from Auxilor's Eco plugin suite).
 * Detects items via PersistentDataContainer key "ecoitems:item".
 * Also detects Talismans, EcoArmor and other Eco family items.
 */
public class EcoItemsHook implements ItemHook {

    private static final NamespacedKey ECO_ITEM = new NamespacedKey("ecoitems", "item");
    private static final NamespacedKey ECO_ARMOR = new NamespacedKey("ecoarmor", "set");
    private static final NamespacedKey TALISMANS = new NamespacedKey("talismans", "talisman");
    private final boolean available;

    public EcoItemsHook() {
        this.available = Bukkit.getPluginManager().getPlugin("EcoItems") != null
                || Bukkit.getPluginManager().getPlugin("EcoArmor") != null
                || Bukkit.getPluginManager().getPlugin("Talismans") != null;
    }

    @Override
    public String getPluginName() {
        return "EcoItems";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean isCustomItem(ItemStack item) {
        return getCustomItemId(item) != null;
    }

    @Override
    public String getCustomItemId(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (pdc.has(ECO_ITEM, PersistentDataType.STRING)) {
            return pdc.get(ECO_ITEM, PersistentDataType.STRING);
        }
        if (pdc.has(ECO_ARMOR, PersistentDataType.STRING)) {
            return "armor:" + pdc.get(ECO_ARMOR, PersistentDataType.STRING);
        }
        if (pdc.has(TALISMANS, PersistentDataType.STRING)) {
            return "talisman:" + pdc.get(TALISMANS, PersistentDataType.STRING);
        }
        return null;
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        return null;
    }
}
