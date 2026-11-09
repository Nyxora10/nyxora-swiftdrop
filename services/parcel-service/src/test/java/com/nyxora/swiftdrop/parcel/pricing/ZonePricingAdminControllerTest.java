package com.nyxora.swiftdrop.parcel.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.exception.ResourceConflictException;
import com.nyxora.swiftdrop.parcel.exception.ResourceNotFoundException;
import com.nyxora.swiftdrop.parcel.pricing.dto.CreatePricingRuleRequest;
import com.nyxora.swiftdrop.parcel.pricing.dto.CreateZoneRequest;
import com.nyxora.swiftdrop.parcel.pricing.dto.UpdatePricingRuleRequest;
import com.nyxora.swiftdrop.parcel.pricing.dto.UpdateZoneRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ZonePricingAdminController MockMvc Tests")
class ZonePricingAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ZonePricingService zonePricingService;

    // ─────────────────────────────────────────────
    //  DELIVERY ZONE ENDPOINTS
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/zones requires ROLE_ADMIN - returns 403 when unauthenticated")
    void getAllZones_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/zones"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/zones with ROLE_ADMIN returns 200 OK")
    void getAllZones_withAdminRole_returns200() throws Exception {
        DeliveryZone zone = DeliveryZone.builder()
                .id(1)
                .zoneCode("WP-COL-01")
                .zoneName("Colombo Central")
                .district("Colombo")
                .baseSurcharge(BigDecimal.ZERO)
                .isActive(true)
                .build();

        when(zonePricingService.getAllZones()).thenReturn(List.of(zone));

        mockMvc.perform(get("/api/v1/zones")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zoneCode").value("WP-COL-01"))
                .andExpect(jsonPath("$[0].district").value("Colombo"));
    }

    @Test
    @DisplayName("POST /api/v1/zones creates zone and returns 201 Created")
    void createZone_withAdminRole_returns201() throws Exception {
        CreateZoneRequest request = CreateZoneRequest.builder()
                .zoneCode("WP-COL-02")
                .zoneName("Colombo Suburbs")
                .district("Colombo")
                .baseSurcharge(new BigDecimal("20.00"))
                .build();

        DeliveryZone created = DeliveryZone.builder()
                .id(2)
                .zoneCode("WP-COL-02")
                .zoneName("Colombo Suburbs")
                .district("Colombo")
                .baseSurcharge(new BigDecimal("20.00"))
                .isActive(true)
                .build();

        when(zonePricingService.createZone(any(CreateZoneRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/zones")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.zoneCode").value("WP-COL-02"));
    }

    @Test
    @DisplayName("PUT /api/v1/zones/{id} updates zone and returns 200 OK")
    void updateZone_withAdminRole_returns200() throws Exception {
        UpdateZoneRequest request = UpdateZoneRequest.builder()
                .zoneName("Colombo Central Renovated")
                .baseSurcharge(new BigDecimal("10.00"))
                .build();

        DeliveryZone updated = DeliveryZone.builder()
                .id(1)
                .zoneCode("WP-COL-01")
                .zoneName("Colombo Central Renovated")
                .district("Colombo")
                .baseSurcharge(new BigDecimal("10.00"))
                .isActive(true)
                .build();

        when(zonePricingService.updateZone(eq(1), any(UpdateZoneRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/zones/1")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneName").value("Colombo Central Renovated"));
    }

    @Test
    @DisplayName("GET /api/v1/zones/{id} returns 404 when the zone is missing")
    void getZone_missing_returns404() throws Exception {
        when(zonePricingService.getZoneById(999))
                .thenThrow(new ResourceNotFoundException("Delivery zone not found: 999"));

        mockMvc.perform(get("/api/v1/zones/999")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    @DisplayName("POST /api/v1/zones returns 409 for a duplicate zone code")
    void createZone_duplicate_returns409() throws Exception {
        CreateZoneRequest request = CreateZoneRequest.builder()
                .zoneCode("WP-COL-02")
                .zoneName("Duplicate Colombo")
                .district("Colombo")
                .baseSurcharge(BigDecimal.ZERO)
                .build();

        when(zonePricingService.createZone(any(CreateZoneRequest.class)))
                .thenThrow(new ResourceConflictException("Zone code already exists: WP-COL-02"));

        mockMvc.perform(post("/api/v1/zones")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource Conflict"));
    }

    @Test
    @DisplayName("PUT /api/v1/zones/{id} validates partial update values")
    void updateZone_negativeSurcharge_returns422() throws Exception {
        mockMvc.perform(put("/api/v1/zones/1")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseSurcharge\":-1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Validation Failed"));

        verifyNoInteractions(zonePricingService);
    }

    // ─────────────────────────────────────────────
    //  PRICING RULE ENDPOINTS
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/pricing/rules with ROLE_ADMIN returns 200 OK")
    void getPricingRules_withAdminRole_returns200() throws Exception {
        PricingRule rule = PricingRule.builder()
                .id(1)
                .baseFee(new BigDecimal("200.00"))
                .ratePerKg(new BigDecimal("30.00"))
                .parcelSize(ParcelSize.SMALL)
                .sizeMultiplier(BigDecimal.ONE)
                .build();

        when(zonePricingService.getPricingRules(null)).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/v1/pricing/rules")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].baseFee").value(200.00));
    }

    @Test
    @DisplayName("POST /api/v1/pricing/rules with ROLE_ADMIN returns 201 Created")
    void createPricingRule_withAdminRole_returns201() throws Exception {
        CreatePricingRuleRequest request = CreatePricingRuleRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .baseFee(new BigDecimal("250.00"))
                .ratePerKg(new BigDecimal("35.00"))
                .parcelSize(ParcelSize.SMALL)
                .sizeMultiplier(BigDecimal.ONE)
                .build();

        PricingRule created = PricingRule.builder()
                .id(10)
                .baseFee(new BigDecimal("250.00"))
                .ratePerKg(new BigDecimal("35.00"))
                .parcelSize(ParcelSize.SMALL)
                .sizeMultiplier(BigDecimal.ONE)
                .build();

        when(zonePricingService.createPricingRule(any(CreatePricingRuleRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/pricing/rules")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.baseFee").value(250.00));
    }

    @Test
    @DisplayName("PUT /api/v1/pricing/rules/{id} with ROLE_ADMIN returns 200 OK")
    void updatePricingRule_withAdminRole_returns200() throws Exception {
        UpdatePricingRuleRequest request = UpdatePricingRuleRequest.builder()
                .baseFee(new BigDecimal("275.00"))
                .ratePerKg(new BigDecimal("40.00"))
                .build();

        PricingRule updated = PricingRule.builder()
                .id(10)
                .baseFee(new BigDecimal("275.00"))
                .ratePerKg(new BigDecimal("40.00"))
                .parcelSize(ParcelSize.SMALL)
                .sizeMultiplier(BigDecimal.ONE)
                .build();

        when(zonePricingService.updatePricingRule(eq(10), any(UpdatePricingRuleRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/pricing/rules/10")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseFee").value(275.00));
    }
}
