package net.nexuby.nexauctionhouse.hook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.inventory.ItemStack;

import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Sends auction events to a Discord channel via webhook.
 * All requests are sent asynchronously to avoid blocking the main thread.
 */
public class DiscordWebhook {

    private final NexAuctionHouse plugin;
    private final HttpClient httpClient;

    public DiscordWebhook(NexAuctionHouse plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private boolean isEnabled() {
        return plugin.getConfigManager().isDiscordEnabled()
                && !plugin.getConfigManager().getDiscordWebhookUrl().isEmpty();
    }

    /**
     * Sends a notification when an item is listed on the auction house.
     */
    public void sendListingNotification(String sellerName, ItemStack item, double price) {
        if (!isEnabled()) return;

        String itemName = AuctionManager.getItemName(item);
        int amount = item.getAmount();
        int color = plugin.getConfigManager().getConfig().getInt("discord.colors.listing", 3447003);

        JsonObject embed = createEmbed(
                "\uD83D\uDCE6 New Listing",
                "**" + sellerName + "** listed **" + itemName + " x" + amount + "** on the auction house for **" + plugin.getEconomyManager().format(price) + "**.",
                color
        );
        addField(embed, "Item", itemName + " x" + amount, true);
        addField(embed, "Price", plugin.getEconomyManager().format(price), true);
        addField(embed, "Seller", sellerName, true);
        addTimestamp(embed);

        sendWebhook(embed);
    }

    /**
     * Sends a notification when an item is sold.
     */
    public void sendSaleNotification(String sellerName, String buyerName, ItemStack item,
                                     double price, double taxAmount) {
        if (!isEnabled()) return;

        String itemName = AuctionManager.getItemName(item);
        int amount = item.getAmount();
        double sellerReceives = price - taxAmount;
        int color = plugin.getConfigManager().getConfig().getInt("discord.colors.sale", 5763719);

        JsonObject embed = createEmbed(
                "\uD83D\uDCB0 Item Sold",
                "**" + buyerName + "** purchased **" + itemName + " x" + amount + "** from **" + sellerName + "** for **" + plugin.getEconomyManager().format(price) + "**.",
                color
        );
        addField(embed, "Item", itemName + " x" + amount, true);
        addField(embed, "Price", plugin.getEconomyManager().format(price), true);
        addField(embed, "Tax", plugin.getEconomyManager().format(taxAmount), true);
        addField(embed, "Seller Receives", plugin.getEconomyManager().format(sellerReceives), true);
        addField(embed, "Seller", sellerName, true);
        addField(embed, "Buyer", buyerName, true);
        addTimestamp(embed);

        sendWebhook(embed);
    }

    /**
     * Sends a notification when an auction is cancelled.
     */
    public void sendCancelNotification(String sellerName, ItemStack item, double price, boolean byAdmin) {
        if (!isEnabled()) return;

        String itemName = AuctionManager.getItemName(item);
        int amount = item.getAmount();
        int color = byAdmin
                ? plugin.getConfigManager().getConfig().getInt("discord.colors.admin", 16776960)
                : plugin.getConfigManager().getConfig().getInt("discord.colors.cancel", 15548997);

        String title = byAdmin ? "\u26A0\uFE0F Auction Removed (Admin)" : "\u274C Auction Cancelled";
        String description = byAdmin
                ? "An admin removed **" + sellerName + "**'s listing for **" + itemName + " x" + amount + "**."
                : "**" + sellerName + "** cancelled their listing for **" + itemName + " x" + amount + "**.";

        JsonObject embed = createEmbed(title, description, color);
        addField(embed, "Item", itemName + " x" + amount, true);
        addField(embed, "Price", plugin.getEconomyManager().format(price), true);
        addField(embed, "Seller", sellerName, true);
        addTimestamp(embed);

        sendWebhook(embed);
    }

    /**
     * Sends a notification when an auction price is updated.
     */
    public void sendPriceUpdateNotification(String sellerName, ItemStack item, double oldPrice, double newPrice) {
        if (!isEnabled()) return;

        String itemName = AuctionManager.getItemName(item);
        int amount = item.getAmount();
        int color = plugin.getConfigManager().getConfig().getInt("discord.colors.listing", 3447003);

        JsonObject embed = createEmbed(
                "\u270F\uFE0F Price Updated",
                "**" + sellerName + "** changed the price of **" + itemName + " x" + amount + "**.",
                color
        );
        addField(embed, "Item", itemName + " x" + amount, true);
        addField(embed, "Old Price", plugin.getEconomyManager().format(oldPrice), true);
        addField(embed, "New Price", plugin.getEconomyManager().format(newPrice), true);
        addField(embed, "Seller", sellerName, true);
        addTimestamp(embed);

        sendWebhook(embed);
    }

    // -- JSON builders --

    private JsonObject createEmbed(String title, String description, int color) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("description", description);
        embed.addProperty("color", color);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "NexAuctionHouse");
        embed.add("footer", footer);

        return embed;
    }

    private void addField(JsonObject embed, String name, String value, boolean inline) {
        JsonArray fields;
        if (embed.has("fields")) {
            fields = embed.getAsJsonArray("fields");
        } else {
            fields = new JsonArray();
            embed.add("fields", fields);
        }

        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        fields.add(field);
    }

    private void addTimestamp(JsonObject embed) {
        embed.addProperty("timestamp", java.time.Instant.now().toString());
    }

    private void sendWebhook(JsonObject embed) {
        String url = plugin.getConfigManager().getDiscordWebhookUrl();

        // Validate that the URL looks like a Discord webhook
        if (!url.startsWith("https://discord.com/api/webhooks/")
                && !url.startsWith("https://discordapp.com/api/webhooks/")) {
            plugin.getLogger().warning("Invalid Discord webhook URL configured.");
            return;
        }

        JsonObject payload = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        String json = payload.toString();

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    plugin.getLogger().warning("Discord webhook returned status " + response.statusCode());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send Discord webhook", e);
            }
        });
    }
}
