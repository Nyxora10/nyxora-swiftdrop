package com.nyxora.swiftdrop.common.security.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe utility providing high-performance Personally Identifiable Information (PII)
 * masking for logs, audit trails, and diagnostics.
 *
 * Conceals:
 * <ul>
 *   <li>Person/recipient names (e.g., {@code Kamal Perera} &rarr; {@code K***l P***a})</li>
 *   <li>Email addresses (e.g., {@code kamal@gmail.com} &rarr; {@code k***l@gmail.com})</li>
 *   <li>Sri Lankan phone numbers (e.g., {@code +94771234567} &rarr; {@code +94*****4567}, {@code 0771234567} &rarr; {@code 07****4567})</li>
 *   <li>Physical residential street numbers (e.g., {@code 123/A Galle Road} &rarr; {@code *** Galle Road})</li>
 *   <li>Free-text log messages with embedded emails and phone numbers</li>
 * </ul>
 */
public final class PiiMaskingUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?<!\\*)\\b([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\b"
    );

    // Matches Sri Lankan local (07), country-code (94), and international-dialing (+94) formats.
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9+])(?:\\+?94[\\s-]?7(?:[\\s-]?\\d){8}|07(?:[\\s-]?\\d){8})(?!\\d)"
    );

    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)(\\bBearer\\s+)[A-Za-z0-9._~+/=-]+"
    );

    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)(\\b(?:password|passwd|secret|token|access[_-]?token|refresh[_-]?token|"
                    + "api[_-]?key|authorization|webhook[_-]?secret|client[_-]?secret)\\b\\s*[=:]\\s*)"
                    + "(?!Bearer\\b)(\"[^\"]*\"|'[^']*'|[^\\s,;}]+)"
    );

    // Matches leading street/house/unit numbers (e.g., "123/A", "45", "No. 12/B", "No 45")
    private static final Pattern LEADING_ADDRESS_NUMBER_PATTERN = Pattern.compile(
            "^(?i)(No\\.?\\s*)?(\\d+([/\\-][a-zA-Z0-9]+)?|[0-9]+[a-zA-Z]?)(?:,\\s*|\\s+)"
    );

    private PiiMaskingUtil() {
        throw new UnsupportedOperationException("PiiMaskingUtil is a utility class and cannot be instantiated.");
    }

    /**
     * Completely redacts a credential, token, or secret for diagnostic output.
     *
     * @param value Sensitive value
     * @return Redaction marker, or the original null/blank state
     */
    public static String redactSecret(String value) {
        if (value == null) {
            return null;
        }
        return value.isBlank() ? value : "[REDACTED]";
    }

    /**
     * Masks an email address while preserving the domain and outer characters of the local part.
     *
     * <ul>
     *   <li>{@code kamal@gmail.com} &rarr; {@code k***l@gmail.com}</li>
     *   <li>{@code ab@test.com} &rarr; {@code a*@test.com}</li>
     *   <li>{@code a@test.com} &rarr; {@code *@test.com}</li>
     * </ul>
     *
     * @param email Raw email address
     * @return Masked email address, or input if null/blank/invalid
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        int atIndex = trimmed.indexOf('@');
        if (atIndex <= 0 || atIndex == trimmed.length() - 1) {
            return trimmed; // Not a standard email format, return as-is
        }

        String localPart = trimmed.substring(0, atIndex);
        String domainPart = trimmed.substring(atIndex); // includes '@'

        String maskedLocal;
        int len = localPart.length();
        if (len == 1) {
            maskedLocal = "*";
        } else if (len == 2) {
            maskedLocal = localPart.charAt(0) + "*";
        } else {
            maskedLocal = localPart.charAt(0) + "***" + localPart.charAt(len - 1);
        }

        return maskedLocal + domainPart;
    }

    /**
     * Masks person or recipient full names while preserving the outer characters of each name token.
     *
     * <ul>
     *   <li>{@code "Kamal Perera"} &rarr; {@code "K***l P***a"}</li>
     *   <li>{@code "Alice"} &rarr; {@code "A***e"}</li>
     *   <li>{@code "Al"} &rarr; {@code "A*"}</li>
     *   <li>{@code "A"} &rarr; {@code "*"}</li>
     *   <li>{@code null} / blank &rarr; returns input safely</li>
     * </ul>
     *
     * @param fullName Raw personal name string
     * @return Privacy-masked name string
     */
    public static String maskName(String fullName) {
        if (fullName == null) {
            return null;
        }
        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String[] parts = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            int len = part.length();

            if (len == 1) {
                sb.append('*');
            } else if (len == 2) {
                sb.append(part.charAt(0)).append('*');
            } else {
                sb.append(part.charAt(0)).append("***").append(part.charAt(len - 1));
            }

            if (i < parts.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /**
     * Masks a Sri Lankan phone number while retaining its country/domestic prefix and trailing digits.
     *
     * <ul>
     *   <li>{@code +94771234567} &rarr; {@code +94*****4567}</li>
     *   <li>{@code 94771234567} &rarr; {@code 94*****4567}</li>
     *   <li>{@code 0771234567} &rarr; {@code 07*****4567}</li>
     *   <li>{@code +94 77 123 4567} &rarr; {@code +94 ** *** 4567}</li>
     *   <li>{@code 077-123-4567} &rarr; {@code 07*-***-4567}</li>
     * </ul>
     *
     * @param phoneNumber Raw phone number string
     * @return Masked phone number, or input if null/blank
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        String trimmed = phoneNumber.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        if (!PHONE_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        // Count actual digits
        int digitCount = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isDigit(trimmed.charAt(i))) {
                digitCount++;
            }
        }

        // If too short to be a valid phone number, return as is
        if (digitCount < 7) {
            return trimmed;
        }

        // Both the Sri Lankan country-code and domestic prefixes contain two digits.
        int prefixDigitsToKeep = 2;
        int suffixDigitsToKeep = 4;

        int digitsSeen = 0;
        StringBuilder sb = new StringBuilder(trimmed.length());

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isDigit(c)) {
                digitsSeen++;
                if (digitsSeen <= prefixDigitsToKeep || digitsSeen > (digitCount - suffixDigitsToKeep)) {
                    sb.append(c);
                } else {
                    sb.append('*');
                }
            } else {
                sb.append(c); // Preserve '+', '-', ' ' and other formatting characters
            }
        }

        return sb.toString();
    }

    /**
     * Masks the residential house, building, or unit number in a physical street address.
     * Preserves street, town, and district context for operational routing.
     *
     * <ul>
     *   <li>{@code 123/A Galle Road, Colombo 03} &rarr; {@code *** Galle Road, Colombo 03}</li>
     *   <li>{@code No. 45/2, 3rd Lane, Kandy} &rarr; {@code No. ***, 3rd Lane, Kandy}</li>
     *   <li>{@code Galle Road, Colombo 03} &rarr; {@code Galle Road, Colombo 03}</li>
     * </ul>
     *
     * @param address Raw physical address
     * @return Masked address, or input if null/blank
     */
    public static String maskAddress(String address) {
        if (address == null) {
            return null;
        }
        String trimmed = address.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        Matcher matcher = LEADING_ADDRESS_NUMBER_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            String prefix = matcher.group(1) != null ? matcher.group(1) : "";
            String matchedMatchedSegment = matcher.group(0);
            boolean hadComma = matchedMatchedSegment.contains(",");
            String remainder = trimmed.substring(matcher.end());

            if (!prefix.isEmpty()) {
                return prefix + "***" + (hadComma ? ", " : " ") + remainder;
            }
            return "*** " + remainder;
        }

        return trimmed;
    }

    /**
     * Scans an arbitrary free-text log message and masks embedded PII and credentials.
     *
     * @param text Raw log text or exception message
     * @return Sanitized text with all PII masked
     */
    public static String maskText(String text) {
        if (text == null) {
            return null;
        }
        if (text.isEmpty()) {
            return text;
        }

        // 1. Mask all emails in text
        Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
        StringBuilder sbEmail = new StringBuilder();
        while (emailMatcher.find()) {
            String matchedEmail = emailMatcher.group(0);
            emailMatcher.appendReplacement(sbEmail, Matcher.quoteReplacement(maskEmail(matchedEmail)));
        }
        emailMatcher.appendTail(sbEmail);

        // 2. Mask all phone numbers in text
        String afterEmailMasking = sbEmail.toString();
        Matcher phoneMatcher = PHONE_PATTERN.matcher(afterEmailMasking);
        StringBuilder sbPhone = new StringBuilder();
        while (phoneMatcher.find()) {
            String matchedPhone = phoneMatcher.group(0);
            phoneMatcher.appendReplacement(sbPhone, Matcher.quoteReplacement(maskPhoneNumber(matchedPhone)));
        }
        phoneMatcher.appendTail(sbPhone);

        // 3. Completely redact credentials and authorization material.
        Matcher bearerMatcher = BEARER_TOKEN_PATTERN.matcher(sbPhone);
        String withBearerRedacted = bearerMatcher.replaceAll("$1[REDACTED]");
        Matcher sensitiveValueMatcher = SENSITIVE_VALUE_PATTERN.matcher(withBearerRedacted);
        return sensitiveValueMatcher.replaceAll("$1[REDACTED]");
    }
}
