package com.nyxora.swiftdrop.parcel.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierRequest;
import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierResponse;
import com.nyxora.swiftdrop.parcel.dispatch.dto.UnassignedParcelResponse;
import com.nyxora.swiftdrop.parcel.dispatch.exception.CourierWorkloadExceededException;
import com.nyxora.swiftdrop.parcel.dispatch.exception.InvalidParcelStateException;
import com.nyxora.swiftdrop.parcel.dispatch.lock.CourierAdvisoryLockManager;
import com.nyxora.swiftdrop.parcel.dispatch.outbox.DispatchOutboxEvent;
import com.nyxora.swiftdrop.parcel.dispatch.outbox.DispatchOutboxPublisher;
import com.nyxora.swiftdrop.parcel.dispatch.outbox.DispatchOutboxRepository;
import com.nyxora.swiftdrop.parcel.dispatch.repository.DispatchParcelRepository;
import com.nyxora.swiftdrop.parcel.dispatch.service.impl.DispatchServiceImpl;
import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DispatchServiceImpl Unit Tests (Member 5: Task 1.6)")
class DispatchServiceImplTest {

    @Mock
    private DispatchParcelRepository dispatchParcelRepository;

    @Mock
    private ParcelStatusHistoryRepository parcelStatusHistoryRepository;

    @Mock
    private CourierAdvisoryLockManager courierAdvisoryLockManager;

    @Mock
    private DispatchOutboxRepository outboxRepository;

    @Mock
    private DispatchOutboxPublisher outboxPublisher;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private DispatchServiceImpl dispatchService;

    private DeliveryZone originZone;
    private DeliveryZone destZone;
    private Parcel sampleParcel;
    private UUID parcelId;
    private UUID courierId;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        parcelId = UUID.randomUUID();
        courierId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();

        originZone = DeliveryZone.builder()
                .id(1)
                .zoneCode("WP-COL-01")
                .zoneName("Colombo Central")
                .build();

        destZone = DeliveryZone.builder()
                .id(2)
                .zoneCode("CP-KDY-01")
                .zoneName("Kandy Central")
                .build();

        sampleParcel = Parcel.builder()
                .id(parcelId)
                .trackingNumber("SD-20261108-123456")
                .senderUserId(UUID.randomUUID())
                .originZone(originZone)
                .destinationZone(destZone)
                .pickupAddress("Colombo 03")
                .dropoffAddress("Kandy")
                .recipientName("Nimal Perera")
                .recipientPhone("+94771234567")
                .weightKg(new BigDecimal("2.50"))
                .parcelSize(ParcelSize.MEDIUM)
                .estimatedFee(new BigDecimal("750.00"))
                .currentStatus(ParcelStatus.CREATED)
                .assignedCourierId(null)
                .build();
    }

    @Test
    @DisplayName("getUnassignedParcels() without zone filter returns all CREATED unassigned parcels")
    void getUnassignedParcels_withoutZone_returnsAllCreatedParcels() {
        when(dispatchParcelRepository.findUnassignedParcels(ParcelStatus.CREATED)).thenReturn(List.of(sampleParcel));

        List<UnassignedParcelResponse> result = dispatchService.getUnassignedParcels(null);

        assertEquals(1, result.size());
        assertEquals("SD-20261108-123456", result.get(0).trackingNumber());
        assertEquals("Colombo Central", result.get(0).originZoneName());
        verify(dispatchParcelRepository, times(1)).findUnassignedParcels(ParcelStatus.CREATED);
    }

    @Test
    @DisplayName("getUnassignedParcels() with zone filter filters parcels by originZoneId")
    void getUnassignedParcels_withZoneFilter_returnsFilteredParcels() {
        when(dispatchParcelRepository.findUnassignedParcelsByOriginZone(ParcelStatus.CREATED, 1))
                .thenReturn(List.of(sampleParcel));

        List<UnassignedParcelResponse> result = dispatchService.getUnassignedParcels(1);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).originZoneId());
        verify(dispatchParcelRepository, times(1)).findUnassignedParcelsByOriginZone(ParcelStatus.CREATED, 1);
    }

    @Test
    @DisplayName("assignCourier() succeeds when courier has 0 active parcels - transitions to ASSIGNED")
    void assignCourier_courierWithZeroActive_succeedsAndTransitionsToAssigned() {
        AssignCourierRequest request = new AssignCourierRequest(courierId);
        when(dispatchParcelRepository.findById(parcelId)).thenReturn(Optional.of(sampleParcel));
        when(dispatchParcelRepository.countByAssignedCourierIdAndCurrentStatusIn(eq(courierId), any(Collection.class))).thenReturn(0L);
        when(dispatchParcelRepository.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any(DispatchOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssignCourierResponse response = dispatchService.assignCourier(parcelId, request, adminUserId);

        assertNotNull(response);
        assertEquals(parcelId, response.parcelId());
        assertEquals(courierId, response.assignedCourierId());
        assertEquals("ASSIGNED", response.status());

        // Verify parcel transitioned to ASSIGNED
        assertEquals(ParcelStatus.ASSIGNED, sampleParcel.getCurrentStatus());
        assertEquals(courierId, sampleParcel.getAssignedCourierId());

        // Verify PostgreSQL advisory lock acquired
        verify(courierAdvisoryLockManager, times(1)).acquireCourierLock(courierId);

        // Verify status history recorded ASSIGNED
        ArgumentCaptor<ParcelStatusHistory> historyCaptor = ArgumentCaptor.forClass(ParcelStatusHistory.class);
        verify(parcelStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        ParcelStatusHistory savedHistory = historyCaptor.getValue();
        assertEquals(ParcelStatus.ASSIGNED, savedHistory.getStatus());
        assertEquals(adminUserId, savedHistory.getUpdatedByUserId());

        // Verify outbox event persisted
        verify(outboxRepository, times(1)).save(any(DispatchOutboxEvent.class));
    }

    @Test
    @DisplayName("assignCourier() succeeds at boundary: active count is 19 (< 20)")
    void assignCourier_courierWith19Active_succeeds() {
        AssignCourierRequest request = new AssignCourierRequest(courierId);
        when(dispatchParcelRepository.findById(parcelId)).thenReturn(Optional.of(sampleParcel));
        when(dispatchParcelRepository.countByAssignedCourierIdAndCurrentStatusIn(eq(courierId), any(Collection.class))).thenReturn(19L);
        when(dispatchParcelRepository.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any(DispatchOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssignCourierResponse response = dispatchService.assignCourier(parcelId, request, adminUserId);

        assertNotNull(response);
        assertEquals("ASSIGNED", response.status());
        assertEquals(ParcelStatus.ASSIGNED, sampleParcel.getCurrentStatus());
    }

    @Test
    @DisplayName("assignCourier() rejects assignment at boundary: active count is 20 (>= 20) with 409 Conflict")
    void assignCourier_courierWith20Active_throwsWorkloadExceededException() {
        AssignCourierRequest request = new AssignCourierRequest(courierId);
        when(dispatchParcelRepository.findById(parcelId)).thenReturn(Optional.of(sampleParcel));
        when(dispatchParcelRepository.countByAssignedCourierIdAndCurrentStatusIn(eq(courierId), any(Collection.class))).thenReturn(20L);

        CourierWorkloadExceededException ex = assertThrows(CourierWorkloadExceededException.class, () ->
                dispatchService.assignCourier(parcelId, request, adminUserId));

        assertEquals(courierId, ex.getCourierId());
        assertEquals(20L, ex.getCurrentActiveCount());

        // Verify state was not modified and no outbox event was created
        verify(dispatchParcelRepository, never()).save(any(Parcel.class));
        verify(parcelStatusHistoryRepository, never()).save(any(ParcelStatusHistory.class));
        verify(outboxRepository, never()).save(any(DispatchOutboxEvent.class));
    }

    @Test
    @DisplayName("assignCourier() rejects assignment when active count is 21 (>= 20) with 409 Conflict")
    void assignCourier_courierWith21Active_throwsWorkloadExceededException() {
        AssignCourierRequest request = new AssignCourierRequest(courierId);
        when(dispatchParcelRepository.findById(parcelId)).thenReturn(Optional.of(sampleParcel));
        when(dispatchParcelRepository.countByAssignedCourierIdAndCurrentStatusIn(eq(courierId), any(Collection.class))).thenReturn(21L);

        assertThrows(CourierWorkloadExceededException.class, () ->
                dispatchService.assignCourier(parcelId, request, adminUserId));

        verify(dispatchParcelRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignCourier() rejects assignment when parcel is not in CREATED status (e.g. IN_TRANSIT)")
    void assignCourier_parcelNotInCreatedStatus_throwsInvalidParcelStateException() {
        sampleParcel.setCurrentStatus(ParcelStatus.IN_TRANSIT);
        AssignCourierRequest request = new AssignCourierRequest(courierId);
        when(dispatchParcelRepository.findById(parcelId)).thenReturn(Optional.of(sampleParcel));

        assertThrows(InvalidParcelStateException.class, () ->
                dispatchService.assignCourier(parcelId, request, adminUserId));

        verify(dispatchParcelRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignCourier() rejects assignment when parcel already has assigned courier")
    void assignCourier_parcelAlreadyAssigned_throwsInvalidParcelStateException() {
        sampleParcel.setAssignedCourierId(UUID.randomUUID());
        AssignCourierRequest request = new AssignCourierRequest(courierId);
        when(dispatchParcelRepository.findById(parcelId)).thenReturn(Optional.of(sampleParcel));

        assertThrows(InvalidParcelStateException.class, () ->
                dispatchService.assignCourier(parcelId, request, adminUserId));

        verify(dispatchParcelRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignCourier() throws exception when parcel ID does not exist")
    void assignCourier_parcelNotFound_throwsIllegalArgumentException() {
        AssignCourierRequest request = new AssignCourierRequest(courierId);
        when(dispatchParcelRepository.findById(parcelId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                dispatchService.assignCourier(parcelId, request, adminUserId));
    }

    @Test
    @DisplayName("Active delivery status set matches Task 1.6 canonical statuses (ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY)")
    void assignCourier_verifiesActiveStatusesQueried() {
        AssignCourierRequest request = new AssignCourierRequest(courierId);
        when(dispatchParcelRepository.findById(parcelId)).thenReturn(Optional.of(sampleParcel));
        when(dispatchParcelRepository.countByAssignedCourierIdAndCurrentStatusIn(eq(courierId), any(Collection.class))).thenReturn(5L);
        when(dispatchParcelRepository.save(any(Parcel.class))).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any(DispatchOutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        dispatchService.assignCourier(parcelId, request, adminUserId);

        ArgumentCaptor<Collection<ParcelStatus>> statusCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(dispatchParcelRepository).countByAssignedCourierIdAndCurrentStatusIn(eq(courierId), statusCaptor.capture());

        Collection<ParcelStatus> capturedStatuses = statusCaptor.getValue();
        assertTrue(capturedStatuses.contains(ParcelStatus.ASSIGNED));
        assertTrue(capturedStatuses.contains(ParcelStatus.PICKED_UP));
        assertTrue(capturedStatuses.contains(ParcelStatus.IN_TRANSIT));
        assertTrue(capturedStatuses.contains(ParcelStatus.OUT_FOR_DELIVERY));
        assertFalse(capturedStatuses.contains(ParcelStatus.CREATED));
        assertFalse(capturedStatuses.contains(ParcelStatus.DELIVERED));
        assertFalse(capturedStatuses.contains(ParcelStatus.FAILED));
    }
}
