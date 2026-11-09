package com.nyxora.swiftdrop.parcel.booking;

import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingNumberGenerator Unit Tests")
class TrackingNumberGeneratorTest {

    @Mock
    private ParcelRepository parcelRepository;

    private TrackingNumberGenerator generator;

    private static final Pattern TRACKING_PATTERN =
            Pattern.compile("^SD-\\d{8}-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$");

    @BeforeEach
    void setUp() {
        generator = new TrackingNumberGenerator(parcelRepository);
    }

    @Test
    @DisplayName("Successfully generates a unique tracking number adhering to SD-YYYYMMDD-XXXXXX format")
    void generateUniqueTrackingNumber_success() {
        when(parcelRepository.existsByTrackingNumber(anyString())).thenReturn(false);

        String trackingNumber = generator.generateUniqueTrackingNumber();

        assertThat(trackingNumber).isNotNull();
        assertThat(trackingNumber).matches(TRACKING_PATTERN);
        verify(parcelRepository, times(1)).existsByTrackingNumber(trackingNumber);
    }

    @Test
    @DisplayName("Retries generation when collision is detected and succeeds on subsequent attempt")
    void generateUniqueTrackingNumber_collisionRetry_success() {
        when(parcelRepository.existsByTrackingNumber(anyString()))
                .thenReturn(true)  // First attempt collides
                .thenReturn(false); // Second attempt is unique

        String trackingNumber = generator.generateUniqueTrackingNumber();

        assertThat(trackingNumber).isNotNull();
        assertThat(trackingNumber).matches(TRACKING_PATTERN);
        verify(parcelRepository, times(2)).existsByTrackingNumber(anyString());
    }

    @Test
    @DisplayName("Throws IllegalStateException when maximum collision retries are exhausted")
    void generateUniqueTrackingNumber_exhaustRetries_throwsException() {
        when(parcelRepository.existsByTrackingNumber(anyString())).thenReturn(true);

        assertThatThrownBy(() -> generator.generateUniqueTrackingNumber())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate a unique tracking number after 5 attempts");

        verify(parcelRepository, times(5)).existsByTrackingNumber(anyString());
    }
}

