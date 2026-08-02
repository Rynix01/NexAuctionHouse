package net.nexuby.nexauctionhouse.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmountRulesTest {

    @Test
    void rejectsNonFiniteAndNegativeAmounts() {
        assertFalse(AmountRules.isNonNegativeFinite(Double.NaN));
        assertFalse(AmountRules.isNonNegativeFinite(Double.POSITIVE_INFINITY));
        assertFalse(AmountRules.isNonNegativeFinite(-0.01));
        assertTrue(AmountRules.isNonNegativeFinite(0));
    }

    @Test
    void integerCurrenciesRejectFractionalOrOverflowingAmounts() {
        assertTrue(AmountRules.isWholeNumber(500, Integer.MAX_VALUE));
        assertTrue(AmountRules.isWholeNumber(95.0000000001, Integer.MAX_VALUE));
        assertFalse(AmountRules.isWholeNumber(1.5, Integer.MAX_VALUE));
        assertFalse(AmountRules.isWholeNumber((double) Integer.MAX_VALUE + 1, Integer.MAX_VALUE));
    }
}
