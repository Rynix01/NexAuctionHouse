package net.nexuby.nexauctionhouse.hook.item;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Detects ExcellentEnchants custom enchantment items. */
public class ExcellentEnchantsHook implements ItemHook {

    private static final NamespacedKey ENCHANT_ID = new NamespacedKey("excellentenchants", "enchant-id");
    private final boolean available = Bukkit.getPluginManager().getPlugin("ExcellentEnchants") != null;

    @Override
    public String getPluginName() {
        return "ExcellentEnchants";
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
        return item.getItemMeta().getPersistentDataContainer().get(ENCHANT_ID, PersistentDataType.STRING);
    }

    @Override
    public String getCustomItemName(ItemStack item) {
        return null;
    }
}
