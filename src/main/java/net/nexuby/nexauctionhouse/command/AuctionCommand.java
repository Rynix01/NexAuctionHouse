package net.nexuby.nexauctionhouse.command;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.config.ConfigManager;
import net.nexuby.nexauctionhouse.config.LangManager;
import net.nexuby.nexauctionhouse.gui.AdminGui;
import net.nexuby.nexauctionhouse.gui.ExpiredGui;
import net.nexuby.nexauctionhouse.gui.MainMenu;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final NexAuctionHouse plugin;

    public AuctionCommand(NexAuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        LangManager lang = plugin.getLangManager();

        // No args -> open main menu
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(lang.prefixed("general.player-only"));
                return true;
            }
            if (!player.hasPermission("nexauctions.use")) {
                player.sendMessage(lang.prefixed("general.no-permission"));
                return true;
            }

            new MainMenu(plugin, player).open();
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "sell" -> handleSell(sender, args);
            case "expired" -> handleExpired(sender);
            case "admin" -> handleAdmin(sender);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(lang.prefixed("general.invalid-usage"));
        }

        return true;
    }

    private void handleSell(CommandSender sender, String[] args) {
        LangManager lang = plugin.getLangManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.prefixed("general.player-only"));
            return;
        }

        if (!player.hasPermission("nexauctions.sell")) {
            player.sendMessage(lang.prefixed("general.no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(lang.prefixed("general.invalid-usage"));
            return;
        }

        // Parse the price
        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(lang.prefixed("general.invalid-usage"));
            return;
        }

        ConfigManager config = plugin.getConfigManager();

        if (price < config.getMinPrice()) {
            player.sendMessage(lang.prefixed("auction.price-too-low",
                    "{min}", plugin.getEconomyManager().format(config.getMinPrice())));
            return;
        }

        if (price > config.getMaxPrice()) {
            player.sendMessage(lang.prefixed("auction.price-too-high",
                    "{max}", plugin.getEconomyManager().format(config.getMaxPrice())));
            return;
        }

        // Check item in hand
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR) {
            player.sendMessage(lang.prefixed("auction.no-item-in-hand"));
            return;
        }

        AuctionManager auctionManager = plugin.getAuctionManager();

        // Blacklist check
        if (!player.hasPermission("nexauctions.bypass.blacklist") && auctionManager.isBlacklisted(itemInHand)) {
            player.sendMessage(lang.prefixed("auction.blacklisted-item"));
            return;
        }

        // Listing limit check
        int limit = auctionManager.getPlayerListingLimit(player);
        int current = auctionManager.getPlayerActiveListings(player.getUniqueId());
        if (current >= limit) {
            player.sendMessage(lang.prefixed("auction.listing-limit-reached",
                    "{limit}", String.valueOf(limit)));
            return;
        }

        // Take item from hand and list it
        ItemStack toSell = itemInHand.clone();
        player.getInventory().setItemInMainHand(null);

        int auctionId = auctionManager.listItem(player, toSell, price);
        if (auctionId > 0) {
            player.sendMessage(lang.prefixed("auction.listed",
                    "{price}", plugin.getEconomyManager().format(price)));
        } else {
            // Something went wrong, give the item back
            player.getInventory().setItemInMainHand(toSell);
            player.sendMessage(lang.prefixed("auction.auction-not-found"));
        }
    }

    private void handleExpired(CommandSender sender) {
        LangManager lang = plugin.getLangManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.prefixed("general.player-only"));
            return;
        }

        if (!player.hasPermission("nexauctions.use")) {
            player.sendMessage(lang.prefixed("general.no-permission"));
            return;
        }

        new ExpiredGui(plugin, player).open();
    }

    private void handleAdmin(CommandSender sender) {
        LangManager lang = plugin.getLangManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.prefixed("general.player-only"));
            return;
        }

        if (!player.hasPermission("nexauctions.admin")) {
            player.sendMessage(lang.prefixed("general.no-permission"));
            return;
        }

        // Admin GUI - shows all auctions with admin controls
        new AdminGui(plugin, player).open();
    }

    private void handleReload(CommandSender sender) {
        LangManager lang = plugin.getLangManager();

        if (!sender.hasPermission("nexauctions.reload")) {
            sender.sendMessage(lang.prefixed("general.no-permission"));
            return;
        }

        plugin.reload();
        sender.sendMessage(lang.prefixed("general.reload"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("sell");
            completions.add("expired");

            if (sender.hasPermission("nexauctions.admin")) {
                completions.add("admin");
            }
            if (sender.hasPermission("nexauctions.reload")) {
                completions.add("reload");
            }

            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.startsWith(input));
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return Arrays.asList("<price>");
        }

        return List.of();
    }
}
