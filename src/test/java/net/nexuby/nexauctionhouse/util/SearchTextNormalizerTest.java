package net.nexuby.nexauctionhouse.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchTextNormalizerTest {

    @Test
    void foldsTurkishDottedIForEnglishMaterialNames() {
        String query = SearchTextNormalizer.normalize("NETHERİTE");
        String material = SearchTextNormalizer.normalize("NETHERITE_SWORD");

        assertEquals("netherite", query);
        assertTrue(material.contains(query));
    }

    @Test
    void foldsTurkishDotlessIAndSeparatesMinecraftIdentifiers() {
        assertEquals("diamond sword", SearchTextNormalizer.normalize("DIAMOND_SWORD"));
        assertEquals("kirik kilic", SearchTextNormalizer.normalize("KIRIK KILIÇ"));
    }

    @Test
    void handlesNullAndRepeatedWhitespace() {
        assertEquals("", SearchTextNormalizer.normalize(null));
        assertEquals("netherite sword", SearchTextNormalizer.normalize("  Netherite   Sword  "));
    }
}
