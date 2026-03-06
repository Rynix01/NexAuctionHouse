package net.nexuby.nexauctionhouse.model;

/**
 * Available sorting modes for the auction house listing display.
 */
public enum SortType {

    NEWEST_FIRST("newest"),
    OLDEST_FIRST("oldest"),
    PRICE_LOW_TO_HIGH("price-asc"),
    PRICE_HIGH_TO_LOW("price-desc"),
    NAME_A_TO_Z("name-asc"),
    NAME_Z_TO_A("name-desc");

    private final String configKey;

    SortType(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    /**
     * Returns the next sort type in the cycle.
     */
    public SortType next() {
        SortType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
