package net.nexuby.nexauctionhouse.migration;

import net.nexuby.nexauctionhouse.NexAuctionHouse;
import net.nexuby.nexauctionhouse.model.AuctionItem;
import net.nexuby.nexauctionhouse.model.AuctionStatus;
import net.nexuby.nexauctionhouse.util.ItemSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Migrates data from CrazyAuctions plugin.
 * Storage: YAML files (data.yml) - items stored as serialized ItemStack maps.
 * Structure: data.yml → Items.<uuid>.<id>.{Item, Price, Seller, ...}
 */
public class CrazyAuctionsMigrator extends AbstractMigrator {

    public CrazyAuctionsMigrator(NexAuctionHouse plugin) {
        super(plugin, "CrazyAuctions");
    }

    @Override
    public String getSourceName() {
        return "CrazyAuctions";
    }

    @Override
    public String validate() {
        File dataFile = resolvePluginFile("CrazyAuctions", "data.yml");
        if (!dataFile.exists()) {
            return "CrazyAuctions data.yml not found at: " + dataFile.getAbsolutePath();
        }
        return null;
    }

    @Override
    public MigrationReport migrate() {
        File dataFile = resolvePluginFile("CrazyAuctions", "data.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);

        migrateSection(data, "Items", false);
        migrateSection(data, "OutOfTime", true);
        migrateSection(data, "Cancelled", true);

        report.finish();
        return report;
    }

    private void migrateSection(YamlConfiguration data, String sectionName, boolean expired) {
        ConfigurationSection section = data.getConfigurationSection(sectionName);
        if (section == null) return;

        // Structure: <section>.<uuid>.<id>.{Item, Price, Seller, Full-Time, Time-Till-Expire}
        for (String uuidStr : section.getKeys(false)) {
            UUID ownerUuid;
            try {
                ownerUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                report.incrementErrors();
                continue;
            }

            ConfigurationSection playerSection = section.getConfigurationSection(uuidStr);
            if (playerSection == null) continue;

            for (String itemId : playerSection.getKeys(false)) {
                try {
                    ConfigurationSection itemSection = playerSection.getConfigurationSection(itemId);
                    if (itemSection == null) continue;

                    ItemStack itemStack = itemSection.getItemStack("Item");
                    if (itemStack == null) {
                        report.incrementErrors();
                        continue;
                    }

                    double price = itemSection.getDouble("Price", 0);
                    String sellerName = itemSection.getString("Seller", "Unknown");

                    if (expired) {
                        insertMigratedExpired(ownerUuid, sellerName, itemStack, "MIGRATED");
                        report.incrementExpired();
                    } else {
                        long now = System.currentTimeMillis();
                        long timeLeft = itemSection.getLong("Time-Till-Expire", 86400000L);
                        long expiresAt = now + timeLeft;

                        AuctionItem item = new AuctionItem(
                                0, ownerUuid, sellerName, itemStack,
                                price, "money", 0, now, expiresAt, AuctionStatus.ACTIVE
                        );

                        int newId = insertMigratedAuction(item);
                        if (newId > 0) {
                            report.incrementAuctions();
                        } else {
                            report.incrementErrors();
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Migration] Error migrating CrazyAuctions item", e);
                    report.incrementErrors();
                }
            }
        }
    }
}
