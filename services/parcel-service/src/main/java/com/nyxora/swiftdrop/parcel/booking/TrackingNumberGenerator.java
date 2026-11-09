package com.nyxora.swiftdrop.parcel.booking;

import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates unique, human-readable, and barcode-compatible linear tracking numbers
 * formatted as {@code SD-YYYYMMDD-XXXXXX} (e.g. {@code SD-20261107-7K9P2X}).
 *
 * <p>Uses an unambiguous Base-32 character set (excluding 0, O, 1, I) to prevent
 * customer confusion when reading tracking numbers from receipts, stickers, or SMS.
 * Guaranteed collision-free via {@link ParcelRepository#existsByTrackingNumber(String)} checks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingNumberGenerator {

    private static final String PREFIX = "SD-";
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int RANDOM_PART_LENGTH = 6;
    private static final int MAX_COLLISION_RETRIES = 5;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final SecureRandom secureRandom = new SecureRandom();
    private final ParcelRepository parcelRepository;

    /**
     * Generates a unique tracking number with guaranteed database uniqueness.
     *
     * @return unique linear tracking number string
     * @throws IllegalStateException if a unique tracking number cannot be generated after maximum retries
     */
    public String generateUniqueTrackingNumber() {
        String datePrefix = PREFIX + DATE_FORMATTER.format(Instant.now()) + "-";

        for (int attempt = 1; attempt <= MAX_COLLISION_RETRIES; attempt++) {
            String candidate = datePrefix + generateRandomSuffix();

            if (!parcelRepository.existsByTrackingNumber(candidate)) {
                log.debug("Generated unique tracking number: {}", candidate);
                return candidate;
            }

            log.warn("Tracking number collision detected for candidate: {} (attempt {}/{})",
                    candidate, attempt, MAX_COLLISION_RETRIES);
        }

        throw new IllegalStateException("Failed to generate a unique tracking number after "
                + MAX_COLLISION_RETRIES + " attempts.");
    }

    private String generateRandomSuffix() {
        char[] chars = new char[RANDOM_PART_LENGTH];
        for (int i = 0; i < RANDOM_PART_LENGTH; i++) {
            chars[i] = ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length()));
        }
        return new String(chars);
    }
}

