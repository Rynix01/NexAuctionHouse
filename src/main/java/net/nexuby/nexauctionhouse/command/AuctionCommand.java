package net.nexuby.nexauctionhouse.command;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.config.ConfigManager;
import net.nexuby.nexauctionhouse.config.LangManager;
import net.nexuby.nexauctionhouse.gui.AdminGui;
import net.nexuby.nexauctionhouse.gui.AdminStatsGui;
import net.nexuby.nexauctionhouse.gui.BlacklistGui;
import net.nexuby.nexauctionhouse.gui.BulkSellGui;
import net.nexuby.nexauctionhouse.gui.BundleCreateGui;
import net.nexuby.nexauctionhouse.gui.ExpiredGui;
import net.nexuby.nexauctionhouse.listener.ChatInputListener;
import net.nexuby.nexauctionhouse.migration.AbstractMigrator;
import net.nexuby.nexauctionhouse.migration.MigrationManager;
import net.nexuby.nexauctionhouse.migration.MigrationReport;
import net.nexuby.nexauctionhouse.gui.FavoritesGui;
import net.nexuby.nexauctionhouse.gui.HistoryGui;
import net.nexuby.nexauctionhouse.gui.MainMenu;
import net.nexuby.nexauctionhouse.gui.NotificationSettingsGui;
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

            // World check
            if (!player.hasPermission("nexauctions.bypass.world") && plugin.getAuctionManager().isWorldDisabled(player)) {
                player.sendMessage(lang.prefixed("blacklist.world-disabled"));
                return true;
            }

            new MainMenu(plugin, player).open();
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "sell" -> handleSell(sender, args);
            case "sell-all" -> handleSellAll(sender, args);
            case "bundle" -> handleBundle(sender, args);
            case "search" -> handleSearch(sender, args);
            case "favorites" -> handleFavorites(sender);
            case "history" -> handleHistory(sender, args);
            case "notifications" -> handleNotifications(sender);
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

        // World check
        if (!player.hasPermission("nexauctions.bypass.world") && plugin.getAuctionManager().isWorldDisabled(player)) {
            player.sendMessage(lang.prefixed("blacklist.world-disabled"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(lang.prefixed("general.invalid-usage"));
            return;
        }

        // Check for --bid and --autorelist flags anywhere in args
        boolean isBidAuction = false;
        boolean isAutoRelist = false;
        List<String> cleanArgs = new ArrayList<>();
        cleanArgs.add(args[0]); // "sell"
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--bid")) {
                isBidAuction = true;
            } else if (args[i].equalsIgnoreCase("--autorelist")) {
                isAutoRelist = true;
            } else {
                cleanArgs.add(args[i]);
            }
        }

        if (isBidAuction && !plugin.getConfigManager().isBidEnabled()) {
            player.sendMessage(lang.prefixed("bid.bid-disabled"));
            return;
        }

        if (isAutoRelist) {
            if (!plugin.getConfigManager().isAutoRelistEnabled()) {
                player.sendMessage(lang.prefixed("auto-relist.feature-disabled"));
                return;
            }
            if (!player.hasPermission("nexauctions.autorelist")) {
                player.sendMessage(lang.prefixed("general.no-permission"));
                return;
            }
        }

        if (cleanArgs.size() < 2) {
            player.sendMessage(lang.prefixed("general.invalid-usage"));
            return;
        }

        // Parse the price
        double price;
        try {
            price = Double.parseDouble(cleanArgs.get(1));
        } catch (NumberFormatException e) {
            player.sendMessage(lang.prefixed("general.invalid-usage"));
            return;
        }

        // Parse currency (optional, defaults to first enabled provider)
        String currency;
        if (cleanArgs.size() >= 3) {
            currency = cleanArgs.get(2).toLowerCase();
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

        // Per-material price limit check
        double[] materialLimits = auctionManager.getMaterialPriceLimits(itemInHand.getType().name());
        if (materialLimits != null) {
            if (price < materialLimits[0]) {
                player.sendMessage(lang.prefixed("blacklist.material-price-too-low",
                        "{material}", itemInHand.getType().name(),
                        "{min}", plugin.getEconomyManager().format(materialLimits[0], currency)));
                return;
            }
            if (price > materialLimits[1]) {
                player.sendMessage(lang.prefixed("blacklist.material-price-too-high",
                        "{material}", itemInHand.getType().name(),
                        "{max}", plugin.getEconomyManager().format(materialLimits[1], currency)));
                return;
            }
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

        int auctionId;
        if (isBidAuction) {
            auctionId = auctionManager.listBidItem(player, toSell, price, currency, isAutoRelist);
        } else {
            auctionId = auctionManager.listItem(player, toSell, price, currency, isAutoRelist);
        }

        if (auctionId > 0) {
            if (isBidAuction) {
                player.sendMessage(lang.prefixed("bid.listed",
                        "{price}", plugin.getEconomyManager().format(price, currency)));
            } else {
                player.sendMessage(lang.prefixed("auction.listed",
                        "{price}", plugin.getEconomyManager().format(price, currency)));
            }
            if (isAutoRelist) {
                player.sendMessage(lang.prefixed("auto-relist.enabled",
                        "{max}", String.valueOf(plugin.getConfigManager().getMaxAutoRelists())));
            }
        } else {
            // Something went wrong, give the item back
            player.getInventory().setItemInMainHand(toSell);
            player.sendMessage(lang.prefixed("auction.auction-not-found"));
        }
    }

    private void handleSellAll(CommandSender sender, String[] args) {
        LangManager lang = plugin.getLangManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.prefixed("general.player-only"));
            return;
        }

        if (!player.hasPermission("nexauctions.sell")) {
            player.sendMessage(lang.prefixed("general.no-permission"));
            return;
        }

        // World check
        if (!player.hasPermission("nexauctions.bypass.world") && plugin.getAuctionManager().isWorldDisabled(player)) {
            player.sendMessage(lang.prefixed("blacklist.world-disabled"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(lang.prefixed("bulk.sell-all-usage"));
            return;
        }

        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(lang.prefixed("auction.invalid-price"));
            return;
        }

        ConfigManager config = plugin.getConfigManager();

        if (price < config.getMinPrice()) {
            player.sendMessage(lang.prefixed("auction.price-too-low",
                    "{min}", String.valueOf(config.getMinPrice())));
            return;
        }

        if (price > config.getMaxPrice()) {
            player.sendMessage(lang.prefixed("auction.price-too-high",
                    "{max}", String.valueOf(config.getMaxPrice())));
            return;
        }

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

        new BulkSellGui(plugin, player, price, currency).open();
    }

    private void handleBundle(CommandSender sender, String[] args) {
        LangManager lang = plugin.getLangManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.prefixed("general.player-only"));
            return;
        }

        if (!player.hasPermission("nexauctions.bundle")) {
            player.sendMessage(lang.prefixed("general.no-permission"));
            return;
        }

        if (!plugin.getConfigManager().isBundleEnabled()) {
            player.sendMessage(lang.prefixed("bundle.disabled"));
            return;
        }

        // World check
        if (!player.hasPermission("nexauctions.bypass.world") && plugin.getAuctionManager().isWorldDisabled(player)) {
            player.sendMessage(lang.prefixed("blacklist.world-disabled"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(lang.prefixed("bundle.usage"));
            return;
        }

        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(lang.prefixed("auction.invalid-price"));
            return;
        }

        ConfigManager config = plugin.getConfigManager();

        if (price < config.getMinPrice()) {
            player.sendMessage(lang.prefixed("auction.price-too-low",
                    "{min}", plugin.getEconomyManager().format(config.getMinPrice(), "money")));
            return;
        }

        if (price > config.getMaxPrice()) {
            player.sendMessage(lang.prefixed("auction.price-too-high",
                    "{max}", plugin.getEconomyManager().format(config.getMaxPrice(), "money")));
            return;
        }

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

        new BundleCreateGui(plugin, player, price, currency).open();
    }

    private void handleHistory(CommandSender sender, String[] args) {
        LangManager lang = plugin.getLangManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.prefixed("general.player-only"));
            return;
        }

        if (!player.hasPermission("nexauctions.use")) {
            player.sendMessage(lang.prefixed("general.no-permission"));
            return;
        }

        // /ah history <player> - admin view
        if (args.length >= 2 && player.hasPermission("nexauctions.admin")) {
            String targetName = args[1];
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                player.sendMessage(lang.prefixed("history.player-not-found"));
                return;
            }
            new HistoryGui(plugin, player, target.getUniqueId(), target.getName()).open();
            return;
        }

        new HistoryGui(plugin, player).open();
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

    private void handleFavorites(CommandSender sender) {
        LangManager lang = plugin.getLangManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.prefixed("general.player-only"));
            return;
        }

        if (!player.hasPermission("nexauctions.use")) {
            player.sendMessage(lang.prefixed("general.no-permission"));
            return;
        }

        new FavoritesGui(plugin, player).open();
    }

    private void handleNotifications(CommandSender sender) {
        LangManager lang = plugin.getLangManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.prefixed("general.player-only"));
            return;
        }

        if (!player.hasPermission("nexauctions.use")) {
            player.sendMessage(lang.prefixed("general.no-permission"));
            return;
        }

        new NotificationSettingsGui(plugin, player, () -> new MainMenu(plugin, player).open()).open();
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
                // Parse flags: --player=<name> or --all
                String targetPlayer = null;
                boolean clearAll = false;
                for (int i = 2; i < args.length; i++) {
                    if (args[i].toLowerCase().startsWith("--player=")) {
                        targetPlayer = args[i].substring("--player=".length());
                    } else if (args[i].equalsIgnoreCase("--all")) {
                        clearAll = true;
                    }
                }

                if (targetPlayer != null) {
                    // Clear specific player's auctions
                    String playerName = targetPlayer;
                    int count = 0;
                    for (AuctionItem auction : new ArrayList<>(plugin.getAuctionManager().getActiveAuctionsList())) {
                        if (auction.getSellerName().equalsIgnoreCase(playerName)) {
                            Player requester = sender instanceof Player p ? p : null;
                            if (plugin.getAuctionManager().cancelAuction(requester, auction.getId(), true)) {
                                count++;
                            }
                        }
                    }
                    sender.sendMessage(lang.prefixed("admin.cleared-player",
                            "{player}", playerName,
                            "{count}", String.valueOf(count)));
                } else if (clearAll) {
                    // Clear all auctions
                    int count = 0;
                    for (AuctionItem auction : new ArrayList<>(plugin.getAuctionManager().getActiveAuctionsList())) {
                        Player requester = sender instanceof Player p ? p : null;
                        if (plugin.getAuctionManager().cancelAuction(requester, auction.getId(), true)) {
                            count++;
                        }
                    }
                    sender.sendMessage(lang.prefixed("admin.cleared", "{count}", String.valueOf(count)));
                } else {
                    // No flag specified - show usage
                    sender.sendMessage(lang.prefixed("admin.clear-usage"));
                }
            }
            case "stats" -> {
                // /ah admin stats - open statistics GUI (player only)
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(lang.prefixed("general.player-only"));
                    return;
                }
                new AdminStatsGui(plugin, player).open();
            }
            case "blacklist" -> {
                // /ah admin blacklist - open blacklist management GUI (player only)
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(lang.prefixed("general.player-only"));
                    return;
                }
                new BlacklistGui(plugin, player).open();
            }
            case "migrate" -> handleMigrate(sender, args);
            default -> sender.sendMessage(lang.prefixed("admin.help"));
        }
    }

    private void handleMigrate(CommandSender sender, String[] args) {
        LangManager lang = plugin.getLangManager();

        MigrationManager migrationManager = plugin.getMigrationManager();

        if (args.length < 3) {
            sender.sendMessage(lang.prefixed("migration.usage",
                    "{plugins}", migrationManager.getSupportedPluginsList()));
            return;
        }

        String pluginName = args[2];
        AbstractMigrator migrator = migrationManager.getMigrator(pluginName);

        if (migrator == null) {
            sender.sendMessage(lang.prefixed("migration.unsupported-plugin",
                    "{plugin}", pluginName,
                    "{plugins}", migrationManager.getSupportedPluginsList()));
            return;
        }

        // Validate source data exists
        String validationError = migrator.validate();
        if (validationError != null) {
            sender.sendMessage(lang.prefixed("migration.validation-failed",
                    "{error}", validationError));
            return;
        }

        if (migrationManager.isMigrationInProgress()) {
            sender.sendMessage(lang.prefixed("migration.already-running"));
            return;
        }

        // Require confirmation
        sender.sendMessage(lang.prefixed("migration.confirm-warning",
                "{plugin}", migrator.getSourceName()));
        sender.sendMessage(lang.prefixed("migration.confirm-prompt"));

        if (sender instanceof Player player) {
            ChatInputListener.awaitInput(player, input -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!input.equalsIgnoreCase("confirm")) {
                        player.sendMessage(lang.prefixed("migration.cancelled"));
                        return;
                    }
                    startMigration(player, migrator, migrationManager);
                });
            });
        } else {
            // Console: run immediately (no chat input needed)
            startMigration(sender, migrator, migrationManager);
        }
    }

    private void startMigration(CommandSender sender, AbstractMigrator migrator, MigrationManager migrationManager) {
        LangManager lang = plugin.getLangManager();

        sender.sendMessage(lang.prefixed("migration.starting",
                "{plugin}", migrator.getSourceName()));

        // Run migration async to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            MigrationReport report = migrationManager.executeMigration(migrator);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (report == null) {
                    sender.sendMessage(lang.prefixed("migration.failed",
                            "{plugin}", migrator.getSourceName()));
                    return;
                }

                sender.sendMessage(lang.prefixed("migration.completed",
                        "{plugin}", report.getSourcePlugin(),
                        "{auctions}", String.valueOf(report.getAuctionsMigrated()),
                        "{expired}", String.valueOf(report.getExpiredMigrated()),
                        "{logs}", String.valueOf(report.getLogsMigrated()),
                        "{errors}", String.valueOf(report.getErrors()),
                        "{time}", String.valueOf(report.getDurationMillis() / 1000)));
            });
        });
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
            completions.add("sell-all");
            completions.add("bundle");
            completions.add("search");
            completions.add("favorites");
            completions.add("history");
            completions.add("notifications");
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

        if (args.length == 2 && args[0].equalsIgnoreCase("sell-all")) {
            return Arrays.asList("<price>");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("bundle")) {
            return Arrays.asList("<price>");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("sell-all")) {
            return new ArrayList<>(plugin.getEconomyManager().getCurrencyNames());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("bundle")) {
            return new ArrayList<>(plugin.getEconomyManager().getCurrencyNames());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("search")) {
            return Arrays.asList("<keyword>");
        }

        // /ah history <player> tab completion for admins
        if (args.length == 2 && args[0].equalsIgnoreCase("history") && sender.hasPermission("nexauctions.admin")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            String input = args[1].toLowerCase();
            names.removeIf(s -> !s.toLowerCase().startsWith(input));
            return names;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("sell")) {
            List<String> currencies = new ArrayList<>(plugin.getEconomyManager().getCurrencyNames());
            currencies.add("--bid");
            currencies.add("--autorelist");
            String input = args[2].toLowerCase();
            currencies.removeIf(s -> !s.toLowerCase().startsWith(input));
            return currencies;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("sell")) {
            List<String> options = new ArrayList<>();
            options.add("--bid");
            options.add("--autorelist");
            String input = args[3].toLowerCase();
            options.removeIf(s -> !s.toLowerCase().startsWith(input));
            return options;
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("sell")) {
            List<String> options = new ArrayList<>();
            options.add("--bid");
            options.add("--autorelist");
            String input = args[4].toLowerCase();
            options.removeIf(s -> !s.toLowerCase().startsWith(input));
            return options;
        }

        // Admin sub-tab completion
        if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("nexauctions.admin")) {
            if (args.length == 2) {
                List<String> subs = new ArrayList<>(List.of("search", "remove", "clear", "stats", "blacklist", "migrate"));
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
                if (args[1].equalsIgnoreCase("clear")) {
                    List<String> clearOptions = new ArrayList<>();
                    clearOptions.add("--all");
                    // Build --player= with online player names
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        clearOptions.add("--player=" + p.getName());
                    }
                    String input = args[2].toLowerCase();
                    clearOptions.removeIf(s -> !s.toLowerCase().startsWith(input));
                    return clearOptions;
                }
                if (args[1].equalsIgnoreCase("migrate")) {
                    List<String> plugins = new ArrayList<>(plugin.getMigrationManager().getSupportedPlugins());
                    String input = args[2].toLowerCase();
                    plugins.removeIf(s -> !s.toLowerCase().startsWith(input));
                    return plugins;
                }
            }
        }

        return List.of();
    }
}
