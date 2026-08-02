package net.nexuby.nexauctionhouse.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectInputFilter;
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
    private static final String SAFE_FORMAT_PREFIX = "v2:";
    private static final int MAX_ITEM_BYTES = 2 * 1024 * 1024;
    private static final int MAX_BUNDLE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_BUNDLE_ITEMS = 256;
    private static final int MAX_BASE64_CHARS = 24 * 1024 * 1024;

    private ItemSerializer() {
        // utility class
    }

    /**
     * Serializes an ItemStack to a Base64 encoded string.
     */
    public static String toBase64(ItemStack itemStack) {
        try {
            byte[] data = itemStack.serializeAsBytes();
            if (data.length > MAX_ITEM_BYTES) {
                throw new IllegalArgumentException("Serialized item exceeds size limit");
            }
            return SAFE_FORMAT_PREFIX + Base64.getEncoder().encodeToString(data);
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
            if (base64.length() > MAX_BASE64_CHARS) {
                throw new IllegalArgumentException("Encoded item exceeds size limit");
            }

            if (base64.startsWith(SAFE_FORMAT_PREFIX)) {
                byte[] data = decodeLimited(base64.substring(SAFE_FORMAT_PREFIX.length()), MAX_ITEM_BYTES);
                return ItemStack.deserializeBytes(data);
            }

            // Backward compatibility for v1 records. The filter blocks unexpected object graphs.
            byte[] data = decodeLimited(base64, MAX_ITEM_BYTES);
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                dataInput.setObjectInputFilter(ItemSerializer::filterLegacyObject);
                Object value = dataInput.readObject();
                return value instanceof ItemStack item ? item : null;
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
        if (items == null || items.size() > MAX_BUNDLE_ITEMS) return null;

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutput = new DataOutputStream(outputStream)) {
            dataOutput.writeInt(items.size());
            for (ItemStack item : items) {
                byte[] itemData = item.serializeAsBytes();
                if (itemData.length > MAX_ITEM_BYTES) {
                    throw new IllegalArgumentException("Serialized bundle item exceeds size limit");
                }
                if (outputStream.size() + itemData.length + Integer.BYTES > MAX_BUNDLE_BYTES) {
                    throw new IllegalArgumentException("Serialized bundle exceeds size limit");
                }
                dataOutput.writeInt(itemData.length);
                dataOutput.write(itemData);
            }
            dataOutput.flush();
            return SAFE_FORMAT_PREFIX + Base64.getEncoder().encodeToString(outputStream.toByteArray());

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
            if (base64.length() > MAX_BASE64_CHARS) {
                throw new IllegalArgumentException("Encoded bundle exceeds size limit");
            }

            if (base64.startsWith(SAFE_FORMAT_PREFIX)) {
                byte[] data = decodeLimited(base64.substring(SAFE_FORMAT_PREFIX.length()), MAX_BUNDLE_BYTES);
                try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
                    int size = input.readInt();
                    if (size < 0 || size > MAX_BUNDLE_ITEMS) {
                        throw new IllegalArgumentException("Invalid bundle size");
                    }

                    List<ItemStack> items = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        int length = input.readInt();
                        if (length < 0 || length > MAX_ITEM_BYTES || length > input.available()) {
                            throw new IllegalArgumentException("Invalid bundle item size");
                        }
                        items.add(ItemStack.deserializeBytes(input.readNBytes(length)));
                    }
                    return items;
                }
            }

            byte[] data = decodeLimited(base64, MAX_ITEM_BYTES);
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                dataInput.setObjectInputFilter(ItemSerializer::filterLegacyObject);
                int size = dataInput.readInt();
                if (size < 0 || size > MAX_BUNDLE_ITEMS) {
                    throw new IllegalArgumentException("Invalid legacy bundle size");
                }
                List<ItemStack> items = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    Object value = dataInput.readObject();
                    if (!(value instanceof ItemStack item)) {
                        throw new IllegalArgumentException("Unexpected legacy bundle entry");
                    }
                    items.add(item);
                }
                return items;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to deserialize bundle from Base64", e);
            return new ArrayList<>();
        }
    }

    private static byte[] decodeLimited(String encoded, int maxBytes) {
        byte[] data = Base64.getDecoder().decode(encoded);
        if (data.length > maxBytes) {
            throw new IllegalArgumentException("Decoded data exceeds size limit");
        }
        return data;
    }

    private static ObjectInputFilter.Status filterLegacyObject(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > 64 || info.references() > 100_000 || info.streamBytes() > MAX_ITEM_BYTES
                || info.arrayLength() > MAX_ITEM_BYTES) {
            return ObjectInputFilter.Status.REJECTED;
        }

        Class<?> type = info.serialClass();
        if (type == null) return ObjectInputFilter.Status.UNDECIDED;
        while (type.isArray()) type = type.getComponentType();
        if (type.isPrimitive()) return ObjectInputFilter.Status.ALLOWED;

        String name = type.getName();
        if (name.startsWith("java.lang.") || name.startsWith("java.util.")
                || name.startsWith("org.bukkit.")) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        return ObjectInputFilter.Status.REJECTED;
    }
}
