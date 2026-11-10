package com.nyxora.swiftdrop.notification.webhook.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HmacSignatureUtil Unit Tests")
class HmacSignatureUtilTest {

    private static final String TEST_SECRET = "whsec_test_secret_key_swift_drop_99";
    private static final String SAMPLE_JSON = """
            {"eventId":"a1b2c3d4-0000-0000-0000-000000000001","trackingNumber":"SD-20261106-000123","status":"DELIVERED"}""";

    @Test
    @DisplayName("Should generate signature in sha256=<lowercase-hex> format")
    void shouldGenerateSignatureInExpectedFormat() {
        String signatureHeader = HmacSignatureUtil.generateSignatureHeader(SAMPLE_JSON, TEST_SECRET);

        assertNotNull(signatureHeader);
        assertTrue(signatureHeader.startsWith(HmacSignatureUtil.SIGNATURE_PREFIX));

        String hexPart = signatureHeader.substring(HmacSignatureUtil.SIGNATURE_PREFIX.length());
        assertEquals(64, hexPart.length(), "HMAC-SHA256 hex digest must be exactly 64 characters");
        assertEquals(hexPart.toLowerCase(), hexPart, "Hex digest must be strictly lowercase");
    }

    @Test
    @DisplayName("Should produce identical signatures for byte array and string inputs")
    void shouldProduceIdenticalSignaturesForBytesAndString() {
        byte[] payloadBytes = SAMPLE_JSON.getBytes(StandardCharsets.UTF_8);

        String fromString = HmacSignatureUtil.generateSignatureHeader(SAMPLE_JSON, TEST_SECRET);
        String fromBytes = HmacSignatureUtil.generateSignatureHeader(payloadBytes, TEST_SECRET);

        assertEquals(fromString, fromBytes);
    }

    @Test
    @DisplayName("Should successfully verify a valid signature")
    void shouldVerifyValidSignatureSuccessfully() {
        String signatureHeader = HmacSignatureUtil.generateSignatureHeader(SAMPLE_JSON, TEST_SECRET);

        boolean isValid = HmacSignatureUtil.verifySignature(SAMPLE_JSON, TEST_SECRET, signatureHeader);

        assertTrue(isValid, "Valid signature must pass verification");
    }

    @Test
    @DisplayName("Should reject tampered payload")
    void shouldRejectTamperedPayload() {
        String signatureHeader = HmacSignatureUtil.generateSignatureHeader(SAMPLE_JSON, TEST_SECRET);
        String tamperedJson = SAMPLE_JSON.replace("DELIVERED", "FAILED");

        boolean isValid = HmacSignatureUtil.verifySignature(tamperedJson, TEST_SECRET, signatureHeader);

        assertFalse(isValid, "Tampered payload must fail signature verification");
    }

    @Test
    @DisplayName("Should reject wrong secret during verification")
    void shouldRejectWrongSecret() {
        String signatureHeader = HmacSignatureUtil.generateSignatureHeader(SAMPLE_JSON, TEST_SECRET);
        String wrongSecret = "whsec_different_secret_key_000000000";

        boolean isValid = HmacSignatureUtil.verifySignature(SAMPLE_JSON, wrongSecret, signatureHeader);

        assertFalse(isValid, "Signature verified with incorrect secret must return false");
    }

    @Test
    @DisplayName("Should reject altered signature digest")
    void shouldRejectAlteredSignatureDigest() {
        String signatureHeader = HmacSignatureUtil.generateSignatureHeader(SAMPLE_JSON, TEST_SECRET);
        // Change one character in the hex part
        char lastChar = signatureHeader.charAt(signatureHeader.length() - 1);
        char replacedChar = (lastChar == 'a') ? 'b' : 'a';
        String corruptedHeader = signatureHeader.substring(0, signatureHeader.length() - 1) + replacedChar;

        boolean isValid = HmacSignatureUtil.verifySignature(SAMPLE_JSON, TEST_SECRET, corruptedHeader);

        assertFalse(isValid, "Altered signature header must fail verification");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "md5=12345", "sha256=", "invalid-format"})
    @DisplayName("Should reject malformed or blank signature headers safely")
    void shouldRejectMalformedSignatureHeaders(String invalidHeader) {
        boolean isValid = HmacSignatureUtil.verifySignature(SAMPLE_JSON, TEST_SECRET, invalidHeader);
        assertFalse(isValid, "Malformed or missing signature header must be safely rejected");
    }

    @Test
    @DisplayName("Should safely reject null payload or secret during verification")
    void shouldSafelyRejectNullInputsInVerification() {
        String validHeader = HmacSignatureUtil.generateSignatureHeader(SAMPLE_JSON, TEST_SECRET);

        assertFalse(HmacSignatureUtil.verifySignature((String) null, TEST_SECRET, validHeader));
        assertFalse(HmacSignatureUtil.verifySignature((byte[]) null, TEST_SECRET, validHeader));
        assertFalse(HmacSignatureUtil.verifySignature(SAMPLE_JSON, null, validHeader));
        assertFalse(HmacSignatureUtil.verifySignature(SAMPLE_JSON, "   ", validHeader));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when calculating signature with invalid arguments")
    void shouldThrowWhenCalculatingWithInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                HmacSignatureUtil.calculateSignatureHex((String) null, TEST_SECRET));

        assertThrows(IllegalArgumentException.class, () ->
                HmacSignatureUtil.calculateSignatureHex((byte[]) null, TEST_SECRET));

        assertThrows(IllegalArgumentException.class, () ->
                HmacSignatureUtil.calculateSignatureHex(SAMPLE_JSON, null));

        assertThrows(IllegalArgumentException.class, () ->
                HmacSignatureUtil.calculateSignatureHex(SAMPLE_JSON, "   "));
    }

    @Test
    @DisplayName("Private constructor should throw UnsupportedOperationException")
    void privateConstructorShouldThrow() throws NoSuchMethodException {
        Constructor<HmacSignatureUtil> constructor = HmacSignatureUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }
}

