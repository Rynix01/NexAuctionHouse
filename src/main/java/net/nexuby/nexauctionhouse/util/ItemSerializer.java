package net.nexuby.nexauctionhouse.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles ItemStack serialization using Bukkit's built-in stream system.
 * This ensures full 1.21+ component data (custom model data, display properties etc.) is preserved.
 */
public final class ItemSerializer {

    private static final Logger LOGGER = Logger.getLogger("NexAuctionHouse");

    private ItemSerializer() {
        // utility class
    }

    /**
     * Serializes an ItemStack to a Base64 encoded string.
     */
    public static String toBase64(ItemStack itemStack) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeObject(itemStack);
            dataOutput.flush();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to serialize ItemStack to Base64", e);
            return null;
        }
    }

    /**
     * Deserializes an ItemStack from a Base64 encoded string.
     */
    public static ItemStack fromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }

        try {
            byte[] data = Base64.getDecoder().decode(base64);
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

                return (ItemStack) dataInput.readObject();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to deserialize ItemStack from Base64", e);
            return null;
        }
    }

    /**
     * Serializes a list of ItemStacks to a single Base64 encoded string.
     * Used for bundle listings that contain multiple items.
     */
    public static String bundleToBase64(List<ItemStack> items) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(items.size());
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.flush();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to serialize bundle to Base64", e);
            return null;
        }
    }

    /**
     * Deserializes a list of ItemStacks from a Base64 encoded string.
     */
    public static List<ItemStack> bundleFromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            byte[] data = Base64.getDecoder().decode(base64);
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

                int size = dataInput.readInt();
                List<ItemStack> items = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    items.add((ItemStack) dataInput.readObject());
                }
                return items;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to deserialize bundle from Base64", e);
            return new ArrayList<>();
        }
    }
}
