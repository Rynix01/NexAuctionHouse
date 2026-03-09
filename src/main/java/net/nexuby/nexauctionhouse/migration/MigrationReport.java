package net.nexuby.nexauctionhouse.migration;

/**
 * Tracks the results of a migration operation, including counts of
 * successfully migrated items, errors, and warnings.
 */
public class MigrationReport {

    private final String sourcePlugin;
    private int auctionsMigrated;
    private int expiredMigrated;
    private int logsMigrated;
    private int errors;
    private long startTime;
    private long endTime;

    public MigrationReport(String sourcePlugin) {
        this.sourcePlugin = sourcePlugin;
        this.startTime = System.currentTimeMillis();
    }

    public void incrementAuctions() { auctionsMigrated++; }
    public void incrementExpired() { expiredMigrated++; }
    public void incrementLogs() { logsMigrated++; }
    public void incrementErrors() { errors++; }

    public void finish() {
        this.endTime = System.currentTimeMillis();
    }

    public String getSourcePlugin() { return sourcePlugin; }
    public int getAuctionsMigrated() { return auctionsMigrated; }
    public int getExpiredMigrated() { return expiredMigrated; }
    public int getLogsMigrated() { return logsMigrated; }
    public int getErrors() { return errors; }
    public int getTotalMigrated() { return auctionsMigrated + expiredMigrated + logsMigrated; }

    public long getDurationMillis() {
        return endTime - startTime;
    }

    public String getSummary() {
        long seconds = getDurationMillis() / 1000;
        return String.format(
                "Migration from %s completed in %ds — Auctions: %d, Expired: %d, Logs: %d, Errors: %d",
                sourcePlugin, seconds, auctionsMigrated, expiredMigrated, logsMigrated, errors
        );
    }
}
