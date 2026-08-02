package net.nexuby.nexauctionhouse.redis;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignedMessageCodecTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void verifiesAuthenticMessagesOnlyOnce() {
        SignedMessageCodec sender = new SignedMessageCodec(SECRET);
        SignedMessageCodec receiver = new SignedMessageCodec(SECRET);
        String envelope = sender.sign("{\"type\":\"UPDATE\"}");

        assertEquals("{\"type\":\"UPDATE\"}", receiver.verify(envelope));
        assertNull(receiver.verify(envelope));
    }

    @Test
    void rejectsTamperedAndWrongKeyMessages() {
        SignedMessageCodec sender = new SignedMessageCodec(SECRET);
        String envelope = sender.sign("payload");

        assertNull(new SignedMessageCodec(SECRET).verify(envelope + "x"));
        assertNull(new SignedMessageCodec("abcdef0123456789abcdef0123456789").verify(envelope));
    }

    @Test
    void rejectsExpiredMessages() {
        AtomicLong clock = new AtomicLong(1_000);
        SignedMessageCodec sender = new SignedMessageCodec(SECRET, clock::get);
        String envelope = sender.sign("payload");
        clock.set(62_000);

        assertNull(new SignedMessageCodec(SECRET, clock::get).verify(envelope));
    }

    @Test
    void requiresStrongSharedSecret() {
        assertThrows(IllegalArgumentException.class, () -> new SignedMessageCodec("short"));
    }
}
