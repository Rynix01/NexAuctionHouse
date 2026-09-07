package net.nexuby.nexauctionhouse.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Produces a stable search key for player-entered text and Minecraft identifiers.
 * This intentionally folds Turkish dotted/dotless i so an English material such as
 * NETHERITE can be found when typed with a Turkish keyboard as NETHERİTE.
 */
public final class SearchTextNormalizer {

    private SearchTextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String folded = value
                .replace('\u0131', 'i')
                .replace('\u0130', 'I')
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ');

        String decomposed = Normalizer.normalize(folded, Normalizer.Form.NFKD);
        return decomposed
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
