package com.nyxora.swiftdrop.notification.webhook.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Reusable cryptographic utility for generating and verifying HMAC-SHA256 webhook signatures.
 *
 * <p>Outbound merchant webhooks are signed using the merchant's private webhook secret.
 * The signature is transmitted in the {@code X-SwiftDrop-Signature} HTTP header in the format:
 * <pre>
 *   X-SwiftDrop-Signature: sha256=&lt;hex-encoded-hmac-sha256&gt;
 * </pre>
 *
 * <p>Security features:
 * <ul>
 *   <li>Uses standard {@link javax.crypto.Mac} with algorithm {@code HmacSHA256}.</li>
 *   <li>Operates on exact UTF-8 payload bytes to preserve JSON byte identity.</li>
 *   <li>Employs constant-time comparison via {@link MessageDigest#isEqual(byte[], byte[])}
 *       to prevent timing attacks.</li>
 *   <li>Never exposes or logs secret material in exception messages or logs.</li>
 * </ul>
 */
public final class HmacSignatureUtil {

    /**
     * Standard HTTP header name for SwiftDrop webhook signatures.
     */
    public static final String SIGNATURE_HEADER_NAME = "X-SwiftDrop-Signature";

    /**
     * Prefix used in the signature header value.
     */
    public static final String SIGNATURE_PREFIX = "sha256=";

    /**
     * Cryptographic algorithm identifier.
     */
    public static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    private HmacSignatureUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Generates the raw lowercase hex-encoded HMAC-SHA256 signature for the given UTF-8 payload.
     *
     * @param payload the raw payload bytes to sign
     * @param secret  the merchant webhook signing secret
     * @return the lowercase hex signature string
     * @throws IllegalArgumentException if payload or secret is null, or secret is empty
     */
    public static String calculateSignatureHex(byte[] payload, String secret) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload bytes must not be null");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Webhook secret must not be null or blank");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256_ALGORITHM
            );
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(payload);
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM environment does not support " + HMAC_SHA256_ALGORITHM, e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid secret key for HMAC computation", e);
        }
    }

    /**
     * Generates the raw lowercase hex-encoded HMAC-SHA256 signature for the given string payload.
     *
     * @param payloadJson the payload string (typically JSON)
     * @param secret      the merchant webhook signing secret
     * @return the lowercase hex signature string
     */
    public static String calculateSignatureHex(String payloadJson, String secret) {
        if (payloadJson == null) {
            throw new IllegalArgumentException("Payload string must not be null");
        }
        return calculateSignatureHex(payloadJson.getBytes(StandardCharsets.UTF_8), secret);
    }

    /**
     * Generates the complete header value formatted as {@code sha256=<hex-signature>}.
     *
     * @param payloadJson the payload string to sign
     * @param secret      the merchant webhook signing secret
     * @return header value formatted as {@code sha256=<hex>}
     */
    public static String generateSignatureHeader(String payloadJson, String secret) {
        String hexSignature = calculateSignatureHex(payloadJson, secret);
        return SIGNATURE_PREFIX + hexSignature;
    }

    /**
     * Generates the complete header value formatted as {@code sha256=<hex-signature>}.
     *
     * @param payload the raw payload bytes to sign
     * @param secret  the merchant webhook signing secret
     * @return header value formatted as {@code sha256=<hex>}
     */
    public static String generateSignatureHeader(byte[] payload, String secret) {
        String hexSignature = calculateSignatureHex(payload, secret);
        return SIGNATURE_PREFIX + hexSignature;
    }

    /**
     * Verifies that the provided {@code X-SwiftDrop-Signature} header matches the expected
     * signature computed from the given payload bytes and merchant secret.
     *
     * <p>Comparison is performed in constant time using {@link MessageDigest#isEqual(byte[], byte[])}
     * to protect against timing analysis attacks.</p>
     *
     * @param payload        the payload bytes to verify
     * @param secret         the merchant webhook secret
     * @param receivedHeader the full received header (e.g. {@code sha256=abcdef...})
     * @return {@code true} if valid and matching; {@code false} if missing, malformed, or mismatched
     */
    public static boolean verifySignature(byte[] payload, String secret, String receivedHeader) {
        if (payload == null || secret == null || secret.isBlank() || receivedHeader == null || receivedHeader.isBlank()) {
            return false;
        }

        String receivedSignature = receivedHeader.trim();
        if (!receivedSignature.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }

        String expectedHeader;
        try {
            expectedHeader = generateSignatureHeader(payload, secret);
        } catch (Exception ex) {
            return false;
        }

        byte[] expectedBytes = expectedHeader.getBytes(StandardCharsets.UTF_8);
        byte[] receivedBytes = receivedSignature.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expectedBytes, receivedBytes);
    }

    /**
     * Verifies that the provided {@code X-SwiftDrop-Signature} header matches the expected
     * signature computed from the given payload string and merchant secret.
     *
     * @param payloadJson    the payload string to verify
     * @param secret         the merchant webhook secret
     * @param receivedHeader the full received header (e.g. {@code sha256=abcdef...})
     * @return {@code true} if valid and matching; {@code false} if missing, malformed, or mismatched
     */
    public static boolean verifySignature(String payloadJson, String secret, String receivedHeader) {
        if (payloadJson == null) {
            return false;
        }
        return verifySignature(payloadJson.getBytes(StandardCharsets.UTF_8), secret, receivedHeader);
    }
}

