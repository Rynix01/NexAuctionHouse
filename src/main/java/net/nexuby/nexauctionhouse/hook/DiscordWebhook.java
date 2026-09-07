package net.nexuby.nexauctionhouse.hook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import org.bukkit.inventory.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/** Sends localized, editable auction event embeds to Discord asynchronously. */
public class DiscordWebhook {

    private final NexAuctionHouse plugin;
    private final HttpClient httpClient;

    public DiscordWebhook(NexAuctionHouse plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private boolean isEnabled() {
        return plugin.getConfigManager().isDiscordEnabled()
                && !plugin.getConfigManager().getDiscordWebhookUrl().isEmpty();
    }

    public void sendListingNotification(String seller, ItemStack item, double price, String currency) {
        if (!isEnabled()) return;
        String itemName = AuctionManager.getItemName(item);
        String amount = String.valueOf(item.getAmount());
        String formattedPrice = plugin.getEconomyManager().format(price, currency);
        JsonObject embed = eventEmbed("listing", "listing",
                "{seller}", seller, "{item}", itemName, "{amount}", amount, "{price}", formattedPrice);
        addField(embed, "item", itemName + " x" + amount);
        addField(embed, "price", formattedPrice);
        addField(embed, "currency", plugin.getEconomyManager().getProvider(currency).getDisplayName());
        addField(embed, "seller", seller);
        finish(embed);
    }

    public void sendSaleNotification(String seller, String buyer, ItemStack item,
                                     double price, double taxAmount, String currency) {
        if (!isEnabled()) return;
        String itemName = AuctionManager.getItemName(item);
        String amount = String.valueOf(item.getAmount());
        String formattedPrice = plugin.getEconomyManager().format(price, currency);
        JsonObject embed = eventEmbed("sale", "sale",
                "{buyer}", buyer, "{seller}", seller, "{item}", itemName,
                "{amount}", amount, "{price}", formattedPrice);
        addField(embed, "item", itemName + " x" + amount);
        addField(embed, "price", formattedPrice);
        addField(embed, "tax", plugin.getEconomyManager().format(taxAmount, currency));
        addField(embed, "seller-receives", plugin.getEconomyManager().format(price - taxAmount, currency));
        addField(embed, "seller", seller);
        addField(embed, "buyer", buyer);
        finish(embed);
    }

    public void sendCancelNotification(String seller, ItemStack item, double price,
                                       boolean byAdmin, String currency) {
        if (!isEnabled()) return;
        String itemName = AuctionManager.getItemName(item);
        String amount = String.valueOf(item.getAmount());
        String event = byAdmin ? "admin-remove" : "cancel";
        String color = byAdmin ? "admin" : "cancel";
        JsonObject embed = eventEmbed(event, color,
                "{seller}", seller, "{item}", itemName, "{amount}", amount);
        addField(embed, "item", itemName + " x" + amount);
        addField(embed, "price", plugin.getEconomyManager().format(price, currency));
        addField(embed, "seller", seller);
        finish(embed);
    }

    public void sendPriceUpdateNotification(String seller, ItemStack item,
                                            double oldPrice, double newPrice, String currency) {
        if (!isEnabled()) return;
        String itemName = AuctionManager.getItemName(item);
        String amount = String.valueOf(item.getAmount());
        JsonObject embed = eventEmbed("price-update", "listing",
                "{seller}", seller, "{item}", itemName, "{amount}", amount);
        addField(embed, "item", itemName + " x" + amount);
        addField(embed, "old-price", plugin.getEconomyManager().format(oldPrice, currency));
        addField(embed, "new-price", plugin.getEconomyManager().format(newPrice, currency));
        addField(embed, "seller", seller);
        finish(embed);
    }

    public void sendBidNotification(String bidder, String seller, ItemStack item,
                                    double bidAmount, String currency) {
        if (!isEnabled()) return;
        String itemName = AuctionManager.getItemName(item);
        String amount = String.valueOf(item.getAmount());
        String formattedPrice = plugin.getEconomyManager().format(bidAmount, currency);
        JsonObject embed = eventEmbed("bid", "listing",
                "{bidder}", bidder, "{seller}", seller, "{item}", itemName,
                "{amount}", amount, "{price}", formattedPrice);
        addField(embed, "item", itemName + " x" + amount);
        addField(embed, "bid-amount", formattedPrice);
        addField(embed, "bidder", bidder);
        addField(embed, "seller", seller);
        finish(embed);
    }

    public void sendAuctionWonNotification(String winner, String seller, ItemStack item,
                                           double finalPrice, String currency) {
        if (!isEnabled()) return;
        String itemName = AuctionManager.getItemName(item);
        String amount = String.valueOf(item.getAmount());
        String formattedPrice = plugin.getEconomyManager().format(finalPrice, currency);
        JsonObject embed = eventEmbed("won", "sale",
                "{winner}", winner, "{seller}", seller, "{item}", itemName,
                "{amount}", amount, "{price}", formattedPrice);
        addField(embed, "item", itemName + " x" + amount);
        addField(embed, "final-price", formattedPrice);
        addField(embed, "winner", winner);
        addField(embed, "seller", seller);
        finish(embed);
    }

    public void sendAutoRelistNotification(String seller, ItemStack item, double price,
                                           int relistCount, String currency) {
        if (!isEnabled()) return;
        String itemName = AuctionManager.getItemName(item);
        String amount = String.valueOf(item.getAmount());
        String formattedPrice = plugin.getEconomyManager().format(price, currency);
        JsonObject embed = eventEmbed("relist", "listing",
                "{seller}", seller, "{item}", itemName, "{amount}", amount, "{price}", formattedPrice);
        addField(embed, "item", itemName + " x" + amount);
        addField(embed, "price", formattedPrice);
        addField(embed, "relist-count", String.valueOf(relistCount));
        addField(embed, "seller", seller);
        finish(embed);
    }

    private JsonObject eventEmbed(String event, String colorKey, String... replacements) {
        int fallback = switch (colorKey) {
            case "sale" -> 5763719;
            case "cancel" -> 15548997;
            case "admin" -> 16776960;
            default -> 3447003;
        };
        int color = plugin.getConfigManager().getConfig().getInt("discord.colors." + colorKey, fallback);
        return createEmbed(message("title." + event), message("description." + event, replacements), color);
    }

    private JsonObject createEmbed(String title, String description, int color) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("description", description);
        embed.addProperty("color", color);
        JsonObject footer = new JsonObject();
        footer.addProperty("text", message("footer"));
        embed.add("footer", footer);
        return embed;
    }

    private void addField(JsonObject embed, String key, String value) {
        JsonArray fields;
        if (embed.has("fields")) fields = embed.getAsJsonArray("fields");
        else {
            fields = new JsonArray();
            embed.add("fields", fields);
        }
        JsonObject field = new JsonObject();
        field.addProperty("name", message("field." + key));
        field.addProperty("value", discordSafe(value));
        field.addProperty("inline", true);
        fields.add(field);
    }

    private void finish(JsonObject embed) {
        embed.addProperty("timestamp", java.time.Instant.now().toString());
        sendWebhook(embed);
    }

    private String message(String key, String... replacements) {
        String[] safe = new String[replacements.length];
        for (int i = 0; i < replacements.length; i++) {
            safe[i] = i % 2 == 0 ? replacements[i] : discordSafe(replacements[i]);
        }
        return plugin.getLangManager().getRaw("discord-webhook." + key, safe);
    }

    private static String discordSafe(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("@", "@\u200B");
    }

    private void sendWebhook(JsonObject embed) {
        String url = plugin.getConfigManager().getDiscordWebhookUrl();
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
