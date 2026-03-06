# NexAuctionHouse

A modern, secure Auction House plugin for Paper 1.21+ servers. Built with performance and user experience in mind.

## Features

- **Full Auction System** — List, browse, buy, and cancel auctions through an intuitive GUI
- **Bid / Auction System** — Create auctions with `--bid` flag. Players bid in real-time with anti-snipe protection. Automatic winner determination and loser refunds on expiry
- **Favorites / Watchlist** — Shift+click any listing to add it to your favorites. Get notified when favorited items are sold, cancelled, or expire. Configurable per-player favorite limit
- **Search & Sort** — Search auctions by item name, material, or seller. Sort by price, date, or name with a single click
- **Multi-Economy Support** — 7 economy providers: Vault, PlayerPoints, TokenManager, CoinsEngine, GemsEconomy, EcoBits, UltraEconomy. Multiple economies active simultaneously with per-listing currency selection
- **Offline Player Sync** — Queued revenue delivery and item returns when players log in. No money or items lost while offline
- **Configurable GUI** — Every menu is driven by YAML configs (slot layout, buttons, filler, lore templates)
- **Category Filtering** — Browse auctions by material type (Blocks, Food, Weapons, Armor, etc.)
- **Pagination** — Smooth page navigation for large auction lists
- **Anti-Dupe Protection** — Click cooldowns, cursor protection, and shadow GUI logic prevent all known duplication exploits
- **Tax System** — Configurable sale tax with per-permission overrides (`nexauctions.tax.<rate>`)
- **VIP Permissions** — Per-group listing limits (`nexauctions.limit.<count>`) and auction duration (`nexauctions.time.<hours>`)
- **Blacklist** — Block specific materials and lore keywords from being listed
- **Multi-Language** — Ships with English and Turkish; add your own by dropping a YAML file in `lang/`
- **Dual Database** — SQLite out of the box, MySQL/MariaDB for networks
- **Expired Item Recovery** — Expired and cancelled items are stored and can be collected later
- **Offline Notifications** — Players are notified on join about pending revenue and uncollected items
- **Discord Webhooks** — Send listing, sale, and cancellation notifications to a Discord channel
- **PlaceholderAPI** — Expose auction data to scoreboards, holograms, and other plugins
- **Admin GUI** — Admins can browse all auctions and force-remove any listing, returning the item to the seller
- **Custom Item Compatibility** — Full support for [ItemsAdder](https://www.spigotmc.org/resources/itemsadder.73355/), [Oraxen](https://www.spigotmc.org/resources/oraxen.72448/), [Nexo](https://www.spigotmc.org/resources/nexo.93464/), [MMOItems](https://www.spigotmc.org/resources/mmoitems.39267/), [MythicMobs](https://www.spigotmc.org/resources/mythicmobs.5702/) / [MythicCrucible](https://www.spigotmc.org/resources/mythiccrucible.91404/), [ExecutableItems](https://www.spigotmc.org/resources/executableitems.77578/), [EcoItems](https://www.spigotmc.org/resources/ecoitems.94648/) / [EcoArmor](https://www.spigotmc.org/resources/ecoarmor.94648/) / [Talismans](https://www.spigotmc.org/resources/talismans.94648/), [Slimefun](https://github.com/Slimefun/Slimefun4), [ModelEngine](https://www.spigotmc.org/resources/conxeptworks-model-engine.79477/), [HeadDatabase](https://www.spigotmc.org/resources/head-database.14280/), [CrazyEnchantments](https://www.spigotmc.org/resources/crazyenchantments.16470/), [ExcellentEnchants](https://www.spigotmc.org/resources/excellentenchants.61693/), and NexEngine/NexItems
- **Crash Protection** — Cursor items are persisted to database in real-time. Server crashes, kicks, and timeouts cannot cause item loss. Rescued items are automatically returned on next login

## Requirements

- Paper 1.21+ (or any fork: Purpur, Folia-compatible coming soon)
- Java 21+
- At least one economy plugin (Vault, PlayerPoints, TokenManager, CoinsEngine, GemsEconomy, EcoBits, or UltraEconomy)

### Optional
- [Vault](https://www.spigotmc.org/resources/vault.34315/) + an economy provider (EssentialsX, CMI, etc.)
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
- [PlayerPoints](https://www.spigotmc.org/resources/playerpoints.80745/)
- [TokenManager](https://www.spigotmc.org/resources/tokenmanager.8610/)
- [CoinsEngine](https://www.spigotmc.org/resources/coinsengine.84121/) (multi-currency)
- [GemsEconomy](https://www.spigotmc.org/resources/gemseconomy.19655/) (multi-currency)
- [EcoBits](https://www.spigotmc.org/resources/ecobits.94672/) (multi-currency)
- [UltraEconomy](https://www.spigotmc.org/resources/ultraeconomy.83374/) (multi-currency)
- [HeadDatabase](https://www.spigotmc.org/resources/head-database.14280/)
- [ItemsAdder](https://www.spigotmc.org/resources/itemsadder.73355/)
- [Oraxen](https://www.spigotmc.org/resources/oraxen.72448/) / [Nexo](https://www.spigotmc.org/resources/nexo.93464/)
- [MMOItems](https://www.spigotmc.org/resources/mmoitems.39267/)
- [MythicMobs](https://www.spigotmc.org/resources/mythicmobs.5702/) / [MythicCrucible](https://www.spigotmc.org/resources/mythiccrucible.91404/)
- [ExecutableItems](https://www.spigotmc.org/resources/executableitems.77578/)
- [EcoItems](https://www.spigotmc.org/resources/ecoitems.94648/) / [EcoArmor](https://www.spigotmc.org/resources/ecoarmor.94648/) / [Talismans](https://www.spigotmc.org/resources/talismans.94648/)
- [Slimefun](https://github.com/Slimefun/Slimefun4)
- [ModelEngine](https://www.spigotmc.org/resources/conxeptworks-model-engine.79477/)
- [CrazyEnchantments](https://www.spigotmc.org/resources/crazyenchantments.16470/)
- [ExcellentEnchants](https://www.spigotmc.org/resources/excellentenchants.61693/)

## Installation

1. Drop `NexAuctionHouse.jar` into your `plugins/` folder
2. Start or restart the server
3. Edit `plugins/NexAuctionHouse/config.yml` to your liking
4. Customize language in `plugins/NexAuctionHouse/lang/en.yml`
5. Adjust GUI layouts in `plugins/NexAuctionHouse/gui/`
6. `/ah reload` to apply changes

## Commands

| Command | Description | Permission |
|---|---|---|
| `/ah` | Open the auction house | `nexauctions.use` |
| `/ah search <keyword>` | Search auctions by keyword | `nexauctions.use` |
| `/ah sell <price>` | List the item in your hand | `nexauctions.sell` |
| `/ah sell <price> <currency>` | List with a specific currency | `nexauctions.sell` |
| `/ah sell <price> --bid` | List as a bid auction | `nexauctions.sell` |
| `/ah sell <price> <currency> --bid` | Bid auction with specific currency | `nexauctions.sell` |
| `/ah favorites` | View your favorites list | `nexauctions.use` |
| `/ah expired` | View & collect expired items | `nexauctions.use` |
| `/ah admin` | Open admin panel | `nexauctions.admin` |
| `/ah reload` | Reload all configs | `nexauctions.reload` |

**Aliases:** `/auctionhouse`, `/nexah`

## Permissions

| Permission | Description | Default |
|---|---|---|
| `nexauctions.use` | Access the auction house | `true` |
| `nexauctions.sell` | List items for sale | `true` |
| `nexauctions.admin` | Access admin panel | `op` |
| `nexauctions.reload` | Reload configuration | `op` |
| `nexauctions.bypass.blacklist` | Bypass blacklisted items | `op` |
| `nexauctions.bypass.tax` | Bypass sale tax | `false` |
| `nexauctions.limit.<number>` | Custom listing limit | — |
| `nexauctions.time.<hours>` | Custom auction duration | — |
| `nexauctions.tax.<rate>` | Custom tax rate | — |

## PlaceholderAPI Placeholders

| Placeholder | Description |
|---|---|
| `%nexauction_total_listings%` | Total active listings on the server |
| `%nexauction_player_listings%` | Number of the player's active listings |
| `%nexauction_player_limit%` | Player's listing limit |
| `%nexauction_player_expired%` | Number of expired items waiting for the player |
| `%nexauction_player_tax%` | Player's tax rate |

## Configuration

### Economy Providers

Multiple economy providers can be active at the same time. Players choose their currency when listing:

```yaml
economy:
  providers:
    vault:
      enabled: true
      display-name: "Money"
      currency-name: "money"
    playerpoints:
      enabled: false
      display-name: "Points"
      currency-name: "points"
    coinsengine:
      enabled: false
      display-name: "Gems"
      currency-name: "gems"
      plugin-currency: "gems"  # Currency ID from CoinsEngine config
```

Supported providers: `vault`, `playerpoints`, `tokenmanager`, `coinsengine`, `gemseconomy`, `ecobits`, `ultraeconomy`

For multi-currency plugins (CoinsEngine, GemsEconomy, EcoBits, UltraEconomy), set `plugin-currency` to match the currency ID in that plugin's config.

### Database (MySQL)

```yaml
database:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: nexauctionhouse
    username: root
    password: 'yourpassword'
    use-ssl: false
```

### Discord Webhooks

```yaml
discord:
  enabled: true
  webhook-url: 'https://discord.com/api/webhooks/...'
  colors:
    sale: 5763719
    listing: 3447003
    cancel: 15548997
    admin: 16776960
```

### Tax System

```yaml
tax:
  enabled: true
  default-rate: 10.0
```

Override per-group with the permission `nexauctions.tax.<rate>` (e.g. `nexauctions.tax.5` for 5%).

## Building from Source

```bash
./gradlew shadowJar
```

The output jar will be in `build/libs/`.

## License

All rights reserved. This plugin is proprietary software by Nexuby.
