# NexAuctionHouse

A modern, secure Auction House plugin for Paper 1.21+ servers. Built with performance and user experience in mind.

## Features

- **Full Auction System** — List, browse, buy, and cancel auctions through an intuitive GUI
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
- **Offline Notifications** — Players are notified on join if they have expired items waiting
- **Discord Webhooks** — Send listing, sale, and cancellation notifications to a Discord channel
- **PlaceholderAPI** — Expose auction data to scoreboards, holograms, and other plugins
- **Admin GUI** — Admins can browse all auctions and force-remove any listing, returning the item to the seller
- **HeadDatabase / ItemsAdder Compatible** — Custom items are preserved through Base64 ItemStack serialization

## Requirements

- Paper 1.21+ (or any fork: Purpur, Folia-compatible coming soon)
- Java 21+
- [Vault](https://www.spigotmc.org/resources/vault.34315/) + an economy provider (EssentialsX, CMI, etc.)

### Optional
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
- [HeadDatabase](https://www.spigotmc.org/resources/head-database.14280/)
- [ItemsAdder](https://www.spigotmc.org/resources/itemsadder.73355/)

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
| `/ah sell <price>` | List the item in your hand | `nexauctions.sell` |
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
