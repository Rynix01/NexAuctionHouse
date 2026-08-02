package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Detects Talismans items through their PDC marker. */
public class TalismansHook implements ItemHook {

    private static final NamespacedKey ITEM_ID = new NamespacedKey("talismans", "talisman");
    private final boolean available = Bukkit.getPluginManager().getPlugin("Talismans") != null;

    @Override
    public String getPluginName() {
        return "Talismans";
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
        return item.getItemMeta().getPersistentDataContainer().get(ITEM_ID, PersistentDataType.STRING);
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        return null;
    }
}
