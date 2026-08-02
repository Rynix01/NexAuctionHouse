package net.nexuby.nexauctionhouse.economy;

public final class AmountRules {

    private AmountRules() {
    }

    public static boolean isNonNegativeFinite(double amount) {
        return Double.isFinite(amount) && amount >= 0;
    }

    public static boolean isWholeNumber(double amount, double maximum) {
        return isNonNegativeFinite(amount) && amount <= maximum
                && Math.abs(amount - Math.rint(amount)) < 1.0E-9;
    }
}
