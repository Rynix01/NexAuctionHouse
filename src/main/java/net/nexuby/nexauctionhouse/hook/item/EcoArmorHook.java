package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Detects EcoArmor set items through their stable PDC marker. */
public class EcoArmorHook implements ItemHook {

    private static final NamespacedKey SET_ID = new NamespacedKey("ecoarmor", "set");
    private final boolean available = Bukkit.getPluginManager().getPlugin("EcoArmor") != null;

    @Override
    public String getPluginName() {
        return "EcoArmor";
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
        return item.getItemMeta().getPersistentDataContainer().get(SET_ID, PersistentDataType.STRING);
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        return null;
    }
}
