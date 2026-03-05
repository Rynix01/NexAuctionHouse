package net.nexuby.nexauctionhouse.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ExpiredItem {

    private final int id;
    private final UUID ownerUuid;
    private final String ownerName;
    private final ItemStack itemStack;
    private final String reason;
    private final long createdAt;

    public ExpiredItem(int id, UUID ownerUuid, String ownerName, ItemStack itemStack, String reason, long createdAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.itemStack = itemStack;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public String getReason() {
        return reason;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
