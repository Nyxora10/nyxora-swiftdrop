package com.nyxora.swiftdrop.parcel.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security Integration Test verifying Spring Security endpoint authorization.
 *
 * Specifically asserts that:
 * - /ws-swiftdrop/** is whitelisted for unauthenticated public clients (SockJS /info handshake).
 * - Public tracking and quote endpoints permit unauthenticated access.
 * - Protected booking operations reject unauthenticated requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Security: Public STOMP SockJS handshake /ws-swiftdrop/info permits unauthenticated access")
    void shouldPermitUnauthenticatedWebSocketHandshake() throws Exception {
        mockMvc.perform(get("/ws-swiftdrop/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security: Public parcel tracking route permits unauthenticated access")
    void shouldPermitUnauthenticatedTrackingRoute() throws Exception {
        // Reaches CustomerParcelController directly (not blocked by Spring Security 401/403)
        mockMvc.perform(get("/api/v1/parcels/track/SWIFT-NONEXISTENT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Security: Protected booking endpoint rejects unauthenticated access with 403 Forbidden")
    void shouldRejectUnauthenticatedBooking() throws Exception {
        mockMvc.perform(post("/api/v1/parcels/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}

