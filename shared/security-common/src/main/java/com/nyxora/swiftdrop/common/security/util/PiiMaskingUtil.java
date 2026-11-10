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
 *   <li>Phone numbers (e.g., {@code +94771234567} &rarr; {@code +94*****4567}, {@code 0771234567} &rarr; {@code 07*****4567})</li>
 *   <li>Physical residential street numbers (e.g., {@code 123/A Galle Road} &rarr; {@code *** Galle Road})</li>
 *   <li>Free-text log messages with embedded emails and phone numbers</li>
 * </ul>
 */
public final class PiiMaskingUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\b"
    );

    // Matches Sri Lankan and international phone numbers (e.g., +94771234567, 0771234567, +94 77 123 4567, 077-123-4567)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:\\+94|0)(?:[\\s\\-]?[0-9]){8,10}\\b"
    );

    // Matches leading street/house/unit numbers (e.g., "123/A", "45", "No. 12/B", "No 45")
    private static final Pattern LEADING_ADDRESS_NUMBER_PATTERN = Pattern.compile(
            "^(?i)(No\\.?\\s*)?(\\d+([/\\-][a-zA-Z0-9]+)?|[0-9]+[a-zA-Z]?)(?:,\\s*|\\s+)"
    );

    private PiiMaskingUtil() {
        throw new UnsupportedOperationException("PiiMaskingUtil is a utility class and cannot be instantiated.");
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
     * Masks a phone number while retaining the country/domestic prefix and trailing digits.
     *
     * <ul>
     *   <li>{@code +94771234567} &rarr; {@code +94*****4567}</li>
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

        // Determine number of unmasked prefix digits (2 for "07...", 3 for "+94...")
        boolean isPlusPrefix = trimmed.startsWith("+");
        int prefixDigitsToKeep = isPlusPrefix ? 2 : 2; // For +94 (9,4 = 2 digits) or 07 (0,7 = 2 digits)
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
     * Scans an arbitrary free-text log message and masks all embedded email addresses
     * and phone numbers.
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

        return sbPhone.toString();
    }
}
