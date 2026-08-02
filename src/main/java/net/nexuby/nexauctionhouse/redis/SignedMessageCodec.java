package net.nexuby.nexauctionhouse.redis;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class SignedMessageCodec {

    private static final long MAX_MESSAGE_AGE_MILLIS = 60_000L;
    private static final int MAX_ENVELOPE_CHARS = 128 * 1024;
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024;

    private final byte[] secret;
    private final LongSupplier clock;
    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();

    public SignedMessageCodec(String secret) {
        this(secret, System::currentTimeMillis);
    }

    SignedMessageCodec(String secret, LongSupplier clock) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Message secret must contain at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String sign(String payload) {
        try {
            long timestamp = clock.getAsLong();
            String nonce = UUID.randomUUID().toString();
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            String signedData = "v1." + timestamp + "." + nonce + "." + encodedPayload;
            return signedData + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(signedData));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign message", e);
        }
    }

    public String verify(String envelope) {
        try {
            if (envelope == null || envelope.length() > MAX_ENVELOPE_CHARS) return null;
            String[] parts = envelope.split("\\.", 5);
            if (parts.length != 5 || !"v1".equals(parts[0])) return null;

            long timestamp = Long.parseLong(parts[1]);
            long now = clock.getAsLong();
            if (Math.abs(now - timestamp) > MAX_MESSAGE_AGE_MILLIS) return null;

            String signedData = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(parts[4]);
            if (!MessageDigest.isEqual(hmac(signedData), suppliedSignature)) return null;

            byte[] payload = Base64.getUrlDecoder().decode(parts[3]);
            if (payload.length > MAX_PAYLOAD_BYTES) return null;

            seenNonces.entrySet().removeIf(entry -> now - entry.getValue() > MAX_MESSAGE_AGE_MILLIS);
            if (seenNonces.putIfAbsent(parts[2], timestamp) != null) return null;
            return new String(payload, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
}
