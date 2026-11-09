package com.nyxora.swiftdrop.parcel.pricing;

import com.nyxora.swiftdrop.parcel.pricing.dto.PublicDeliveryZoneResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("PublicZoneController MockMvc Tests")
class PublicZoneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicZoneQueryService publicZoneQueryService;

    @Test
    @DisplayName("GET /api/v1/public/zones - Anonymous access returns 200 OK with active delivery zones")
    void getPublicZones_Anonymous_Returns200WithZones() throws Exception {
        PublicDeliveryZoneResponse zone1 = PublicDeliveryZoneResponse.builder()
                .id(1)
                .zoneCode("WP-COL-01")
                .zoneName("Colombo Central Metro")
                .district("Colombo")
                .build();

        PublicDeliveryZoneResponse zone2 = PublicDeliveryZoneResponse.builder()
                .id(2)
                .zoneCode("WP-GAM-01")
                .zoneName("Gampaha Industrial Hub")
                .district("Gampaha")
                .build();

        when(publicZoneQueryService.getActiveZones(null, null))
                .thenReturn(List.of(zone1, zone2));

        mockMvc.perform(get("/api/v1/public/zones")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].zoneCode").value("WP-COL-01"))
                .andExpect(jsonPath("$[0].zoneName").value("Colombo Central Metro"))
                .andExpect(jsonPath("$[0].district").value("Colombo"))
                // Ensure internal/sensitive fields are not exposed
                .andExpect(jsonPath("$[0].baseSurcharge").doesNotExist())
                .andExpect(jsonPath("$[0].isActive").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/public/zones?search=Colombo - Returns matching active zones")
    void getPublicZones_WithSearch_ReturnsMatching() throws Exception {
        PublicDeliveryZoneResponse zone1 = PublicDeliveryZoneResponse.builder()
                .id(1)
                .zoneCode("WP-COL-01")
                .zoneName("Colombo Central Metro")
                .district("Colombo")
                .build();

        when(publicZoneQueryService.getActiveZones("Colombo", null))
                .thenReturn(List.of(zone1));

        mockMvc.perform(get("/api/v1/public/zones")
                        .param("search", "Colombo")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].zoneName").value("Colombo Central Metro"));
    }

    @Test
    @DisplayName("GET /api/v1/public/zones?district=Kandy - Returns district zones")
    void getPublicZones_WithDistrict_ReturnsMatching() throws Exception {
        PublicDeliveryZoneResponse zone3 = PublicDeliveryZoneResponse.builder()
                .id(3)
                .zoneCode("CP-KAN-01")
                .zoneName("Kandy Central Highlands")
                .district("Kandy")
                .build();

        when(publicZoneQueryService.getActiveZones(null, "Kandy"))
                .thenReturn(List.of(zone3));

        mockMvc.perform(get("/api/v1/public/zones")
                        .param("district", "Kandy")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].district").value("Kandy"));
    }

    @Test
    @DisplayName("GET /api/v1/public/zones?search=NonExistent - Returns 200 OK with empty array")
    void getPublicZones_NoMatches_ReturnsEmptyArray() throws Exception {
        when(publicZoneQueryService.getActiveZones("NonExistent", null))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/public/zones")
                        .param("search", "NonExistent")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/public/zones is rejected for anonymous callers")
    void postPublicZones_Anonymous_Rejected() throws Exception {
        mockMvc.perform(post("/api/v1/public/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}

