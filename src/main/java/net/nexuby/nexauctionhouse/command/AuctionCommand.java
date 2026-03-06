package net.nexuby.nexauctionhouse.command;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.config.ConfigManager;
import net.nexuby.nexauctionhouse.config.LangManager;
import net.nexuby.nexauctionhouse.gui.AdminGui;
import net.nexuby.nexauctionhouse.gui.ExpiredGui;
import net.nexuby.nexauctionhouse.gui.MainMenu;
import net.nexuby.nexauctionhouse.manager.AuctionManager;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import org.bukkit.Bukkit;
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
            case "search" -> handleSearch(sender, args);
            case "expired" -> handleExpired(sender);
            case "admin" -> handleAdmin(sender, args);
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

        // Parse currency (optional, defaults to first enabled provider)
        String currency;
        if (args.length >= 3) {
            currency = args[2].toLowerCase();
            if (!plugin.getEconomyManager().isValidCurrency(currency)) {
                player.sendMessage(lang.prefixed("auction.invalid-currency",
                        "{currencies}", String.join(", ", plugin.getEconomyManager().getCurrencyNames())));
                return;
            }
        } else {
            currency = plugin.getEconomyManager().getDefaultProvider().getCurrencyName();
        }

        ConfigManager config = plugin.getConfigManager();

        if (price < config.getMinPrice()) {
            player.sendMessage(lang.prefixed("auction.price-too-low",
                    "{min}", plugin.getEconomyManager().format(config.getMinPrice(), currency)));
            return;
        }

        if (price > config.getMaxPrice()) {
            player.sendMessage(lang.prefixed("auction.price-too-high",
                    "{max}", plugin.getEconomyManager().format(config.getMaxPrice(), currency)));
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

        int auctionId = auctionManager.listItem(player, toSell, price, currency);
        if (auctionId > 0) {
            player.sendMessage(lang.prefixed("auction.listed",
                    "{price}", plugin.getEconomyManager().format(price, currency)));
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

    private void handleSearch(CommandSender sender, String[] args) {
        LangManager lang = plugin.getLangManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.prefixed("general.player-only"));
            return;
        }

        if (!player.hasPermission("nexauctions.use")) {
            player.sendMessage(lang.prefixed("general.no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(lang.prefixed("search.search-usage"));
            return;
        }

        // Join all args after "search" as the query
        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        new MainMenu(plugin, player, query).open();
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        LangManager lang = plugin.getLangManager();

        if (!sender.hasPermission("nexauctions.admin")) {
            sender.sendMessage(lang.prefixed("general.no-permission"));
            return;
        }

        // /ah admin - open admin GUI (player only)
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(lang.prefixed("general.player-only"));
                return;
            }
            new AdminGui(plugin, player).open();
            return;
        }

        String adminSub = args[1].toLowerCase();

        switch (adminSub) {
            case "search" -> {
                // /ah admin search <player> - opens admin GUI filtered to a player
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(lang.prefixed("general.player-only"));
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(lang.prefixed("admin.search-usage"));
                    return;
                }
                new AdminGui(plugin, player, args[2]).open();
            }
            case "remove" -> {
                // /ah admin remove <id> - remove an auction by ID
                if (args.length < 3) {
                    sender.sendMessage(lang.prefixed("admin.remove-usage"));
                    return;
                }
                int auctionId;
                try {
                    auctionId = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(lang.prefixed("admin.invalid-id"));
                    return;
                }
                AuctionItem auction = plugin.getAuctionManager().getAuction(auctionId);
                if (auction == null) {
                    sender.sendMessage(lang.prefixed("auction.auction-not-found"));
                    return;
                }
                Player requester = sender instanceof Player p ? p : null;
                boolean removed = plugin.getAuctionManager().cancelAuction(requester, auctionId, true);
                if (removed) {
                    sender.sendMessage(lang.prefixed("admin.removed",
                            "{player}", auction.getSellerName()));
                    Player seller = Bukkit.getPlayer(auction.getSellerUuid());
                    if (seller != null && seller.isOnline()) {
                        seller.sendMessage(lang.prefixed("admin.force-removed",
                                "{item}", AuctionManager.getItemName(auction.getItemStack())));
                    }
                }
            }
            case "clear" -> {
                // /ah admin clear - clear all active auctions, return items
                int count = 0;
                for (AuctionItem auction : new ArrayList<>(plugin.getAuctionManager().getActiveAuctionsList())) {
                    Player requester = sender instanceof Player p ? p : null;
                    if (plugin.getAuctionManager().cancelAuction(requester, auction.getId(), true)) {
                        count++;
                    }
                }
                sender.sendMessage(lang.prefixed("admin.cleared", "{count}", String.valueOf(count)));
            }
            case "stats" -> {
                // /ah admin stats - show auction statistics
                AuctionManager manager = plugin.getAuctionManager();
                int active = manager.getActiveAuctionsList().size();
                double totalValue = 0;
                for (AuctionItem item : manager.getActiveAuctionsList()) {
                    totalValue += item.getPrice();
                }
                sender.sendMessage(lang.prefixed("admin.stats-active", "{count}", String.valueOf(active)));
                sender.sendMessage(lang.prefixed("admin.stats-value",
                        "{value}", plugin.getEconomyManager().format(totalValue)));
            }
            default -> sender.sendMessage(lang.prefixed("admin.help"));
        }
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
            completions.add("search");
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

        if (args.length == 2 && args[0].equalsIgnoreCase("search")) {
            return Arrays.asList("<keyword>");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("sell")) {
            List<String> currencies = new ArrayList<>(plugin.getEconomyManager().getCurrencyNames());
            String input = args[2].toLowerCase();
            currencies.removeIf(s -> !s.startsWith(input));
            return currencies;
        }

        // Admin sub-tab completion
        if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("nexauctions.admin")) {
            if (args.length == 2) {
                List<String> subs = new ArrayList<>(List.of("search", "remove", "clear", "stats"));
                String input = args[1].toLowerCase();
                subs.removeIf(s -> !s.startsWith(input));
                return subs;
            }
            if (args.length == 3) {
                if (args[1].equalsIgnoreCase("search")) {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        names.add(p.getName());
                    }
                    String input = args[2].toLowerCase();
                    names.removeIf(s -> !s.toLowerCase().startsWith(input));
                    return names;
                }
                if (args[1].equalsIgnoreCase("remove")) {
                    return List.of("<id>");
                }
            }
        }

        return List.of();
    }
}
