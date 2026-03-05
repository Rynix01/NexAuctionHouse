package net.nexuby.nexauctionhouse.util;

import net.nexuby.nexauctionhouse.NexAuctionHouse;

/**
 * Formats time durations into human readable strings using the language config.
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    /**
     * Formats milliseconds into a short readable string like "23h 15m".
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return NexAuctionHouse.getInstance().getLangManager().getRaw("time.expired");
        }

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        minutes = minutes % 60;

        StringBuilder sb = new StringBuilder();

        if (hours > 0) {
            sb.append(NexAuctionHouse.getInstance().getLangManager()
                    .getRaw("time.hours", "{value}", String.valueOf(hours)));
        }

        if (minutes > 0 || hours == 0) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(NexAuctionHouse.getInstance().getLangManager()
                    .getRaw("time.minutes", "{value}", String.valueOf(minutes)));
        }

        return sb.toString();
    }
}
