package com.nyxora.swiftdrop.parcel.courier;

import com.nyxora.swiftdrop.parcel.booking.config.RabbitMqConfig;
import com.nyxora.swiftdrop.parcel.courier.dto.CourierParcelCardDto;
import com.nyxora.swiftdrop.parcel.courier.dto.CourierRunsheetResponseDto;
import com.nyxora.swiftdrop.parcel.courier.dto.ParcelStatusUpdateResponseDto;
import com.nyxora.swiftdrop.parcel.courier.dto.UpdateParcelStatusRequestDto;
import com.nyxora.swiftdrop.parcel.courier.event.ParcelStatusUpdatedEvent;
import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
import com.nyxora.swiftdrop.parcel.statemachine.InvalidStateTransitionException;
import com.nyxora.swiftdrop.parcel.statemachine.ParcelStateMachine;
import com.nyxora.swiftdrop.parcel.websocket.event.ParcelStatusChangedLocalEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourierExecutionService Unit Tests (Member 6)")
class CourierExecutionServiceTest {

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private ParcelStatusHistoryRepository parcelStatusHistoryRepository;

    @Mock
    private ParcelStateMachine parcelStateMachine;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CourierExecutionServiceImpl courierExecutionService;

    private UUID courierId;
    private UUID parcelId;
    private DeliveryZone originZone;
    private DeliveryZone destZone;

    @BeforeEach
    void setUp() {
        courierId = UUID.randomUUID();
        parcelId = UUID.randomUUID();

        originZone = DeliveryZone.builder()
                .id(1)
                .zoneCode("COL-01")
                .zoneName("Colombo Fort")
                .build();

        destZone = DeliveryZone.builder()
                .id(2)
                .zoneCode("COL-03")
                .zoneName("Kollupitiya")
                .build();
    }

    private Parcel buildTestParcel(UUID pId, ParcelStatus status, UUID assignedCourier) {
        return Parcel.builder()
                .id(pId)
                .trackingNumber("SD-20261109-TEST01")
                .senderUserId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .assignedCourierId(assignedCourier)
                .currentStatus(status)
                .originZone(originZone)
                .destinationZone(destZone)
                .pickupAddress("123 Main St, Colombo 01")
                .dropoffAddress("456 Galle Rd, Colombo 03")
                .recipientName("Nimal Silva")
                .recipientPhone("+94771234567")
                .weightKg(new BigDecimal("2.50"))
                .parcelSize(ParcelSize.MEDIUM)
                .estimatedFee(new BigDecimal("650.00"))
                .build();
    }

    @Nested
    @DisplayName("1. getCourierRunsheet Tests")
    class GetCourierRunsheetTests {

        @Test
        @DisplayName("Throws IllegalArgumentException if courierId is null")
        void nullCourierIdThrowsException() {
            assertThatThrownBy(() -> courierExecutionService.getCourierRunsheet(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Courier ID cannot be null");
        }

        @Test
        @DisplayName("Returns empty runsheet if courier has no assigned parcels")
        void emptyRunsheet() {
            when(parcelRepository.findByAssignedCourierId(courierId)).thenReturn(Collections.emptyList());

            CourierRunsheetResponseDto response = courierExecutionService.getCourierRunsheet(courierId);

            assertThat(response).isNotNull();
            assertThat(response.getCourierId()).isEqualTo(courierId);
            assertThat(response.getTotalActiveParcels()).isZero();
            assertThat(response.getPickups()).isEmpty();
            assertThat(response.getDeliveries()).isEmpty();
            assertThat(response.getGeneratedAt()).isNotNull();
        }

        @Test
        @DisplayName("Partitions parcels correctly into pickups and deliveries, filtering out terminal statuses")
        void partitionsRunsheetCorrectly() {
            Parcel p1 = buildTestParcel(UUID.randomUUID(), ParcelStatus.ASSIGNED, courierId);
            Parcel p2 = buildTestParcel(UUID.randomUUID(), ParcelStatus.CREATED, courierId);
            Parcel p3 = buildTestParcel(UUID.randomUUID(), ParcelStatus.PICKED_UP, courierId);
            Parcel p4 = buildTestParcel(UUID.randomUUID(), ParcelStatus.IN_TRANSIT, courierId);
            Parcel p5 = buildTestParcel(UUID.randomUUID(), ParcelStatus.OUT_FOR_DELIVERY, courierId);
            Parcel p6 = buildTestParcel(UUID.randomUUID(), ParcelStatus.FAILED, courierId);
            Parcel p7 = buildTestParcel(UUID.randomUUID(), ParcelStatus.DELIVERED, courierId);
            Parcel p8 = buildTestParcel(UUID.randomUUID(), ParcelStatus.RETURNED_TO_SENDER, courierId);

            when(parcelRepository.findByAssignedCourierId(courierId))
                    .thenReturn(Arrays.asList(p1, p2, p3, p4, p5, p6, p7, p8));
            when(parcelStatusHistoryRepository.countByParcel_IdAndStatus(any(), eq(ParcelStatus.FAILED))).thenReturn(0L);

            CourierRunsheetResponseDto response = courierExecutionService.getCourierRunsheet(courierId);

            assertThat(response.getTotalActiveParcels()).isEqualTo(6);
            assertThat(response.getPickups()).hasSize(2); // ASSIGNED, CREATED
            assertThat(response.getDeliveries()).hasSize(4); // PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, FAILED
        }

        @Test
        @DisplayName("Handles parcels with null zones gracefully")
        void runsheetWithNullZones() {
            Parcel p = buildTestParcel(UUID.randomUUID(), ParcelStatus.ASSIGNED, courierId);
            p.setOriginZone(null);
            p.setDestinationZone(null);

            when(parcelRepository.findByAssignedCourierId(courierId)).thenReturn(Collections.singletonList(p));
            when(parcelStatusHistoryRepository.countByParcel_IdAndStatus(any(), eq(ParcelStatus.FAILED))).thenReturn(1L);

            CourierRunsheetResponseDto response = courierExecutionService.getCourierRunsheet(courierId);

            assertThat(response.getPickups()).hasSize(1);
            CourierParcelCardDto card = response.getPickups().get(0);
            assertThat(card.getOriginZoneName()).isNull();
            assertThat(card.getDestinationZoneName()).isNull();
            assertThat(card.getFailureCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("2. getCourierParcelById Tests")
    class GetCourierParcelByIdTests {

        @Test
        @DisplayName("Validation errors for null identifiers")
        void validationErrors() {
            assertThatThrownBy(() -> courierExecutionService.getCourierParcelById(null, parcelId))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> courierExecutionService.getCourierParcelById(courierId, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Throws IllegalArgumentException if parcel not found")
        void parcelNotFound() {
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> courierExecutionService.getCourierParcelById(courierId, parcelId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Throws AccessDeniedException if parcel assigned to different courier or unassigned")
        void accessDeniedWhenCourierMismatch() {
            UUID otherCourier = UUID.randomUUID();
            Parcel parcelOther = buildTestParcel(parcelId, ParcelStatus.ASSIGNED, otherCourier);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcelOther));

            assertThatThrownBy(() -> courierExecutionService.getCourierParcelById(courierId, parcelId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Access denied");

            Parcel unassignedParcel = buildTestParcel(parcelId, ParcelStatus.CREATED, null);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(unassignedParcel));

            assertThatThrownBy(() -> courierExecutionService.getCourierParcelById(courierId, parcelId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Access denied");
        }

        @Test
        @DisplayName("Returns parcel details if assigned to requesting courier")
        void returnsParcelCard() {
            Parcel parcel = buildTestParcel(parcelId, ParcelStatus.OUT_FOR_DELIVERY, courierId);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
            when(parcelStatusHistoryRepository.countByParcel_IdAndStatus(parcelId, ParcelStatus.FAILED)).thenReturn(0L);

            CourierParcelCardDto card = courierExecutionService.getCourierParcelById(courierId, parcelId);

            assertThat(card).isNotNull();
            assertThat(card.getId()).isEqualTo(parcelId);
            assertThat(card.getCurrentStatus()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
            assertThat(card.getOriginZoneName()).isEqualTo("Colombo Fort");
            assertThat(card.getDestinationZoneName()).isEqualTo("Kollupitiya");
        }
    }

    @Nested
    @DisplayName("3. updateParcelStatus Tests")
    class UpdateParcelStatusTests {

        @Test
        @DisplayName("Validation errors for null arguments")
        void validationErrors() {
            UpdateParcelStatusRequestDto validReq = UpdateParcelStatusRequestDto.builder()
                    .targetStatus(ParcelStatus.PICKED_UP).build();

            assertThatThrownBy(() -> courierExecutionService.updateParcelStatus(null, parcelId, validReq))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> courierExecutionService.updateParcelStatus(courierId, null, validReq))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> courierExecutionService.updateParcelStatus(courierId, parcelId, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> courierExecutionService.updateParcelStatus(courierId, parcelId,
                    UpdateParcelStatusRequestDto.builder().build()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Throws AccessDeniedException if parcel assigned to different courier")
        void accessDeniedOnUpdate() {
            UUID otherCourier = UUID.randomUUID();
            Parcel parcelOther = buildTestParcel(parcelId, ParcelStatus.ASSIGNED, otherCourier);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcelOther));

            UpdateParcelStatusRequestDto req = UpdateParcelStatusRequestDto.builder()
                    .targetStatus(ParcelStatus.PICKED_UP).build();

            assertThatThrownBy(() -> courierExecutionService.updateParcelStatus(courierId, parcelId, req))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Successful status transition from ASSIGNED to PICKED_UP")
        void successfulTransitionToPickedUp() {
            Parcel parcel = buildTestParcel(parcelId, ParcelStatus.ASSIGNED, courierId);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
            when(parcelStatusHistoryRepository.countByParcel_IdAndStatus(parcelId, ParcelStatus.FAILED)).thenReturn(0L);

            UpdateParcelStatusRequestDto req = UpdateParcelStatusRequestDto.builder()
                    .targetStatus(ParcelStatus.PICKED_UP)
                    .deliveryNote("Picked up from sender office")
                    .build();

            ParcelStatusUpdateResponseDto response = courierExecutionService.updateParcelStatus(courierId, parcelId, req);

            assertThat(response).isNotNull();
            assertThat(response.getParcelId()).isEqualTo(parcelId);
            assertThat(response.getPreviousStatus()).isEqualTo(ParcelStatus.ASSIGNED);
            assertThat(response.getCurrentStatus()).isEqualTo(ParcelStatus.PICKED_UP);

            verify(parcelStateMachine).validateTransition(ParcelStatus.ASSIGNED, ParcelStatus.PICKED_UP, null, null, null, 0);
            verify(parcelRepository).save(parcel);
            verify(parcelStatusHistoryRepository).save(any(ParcelStatusHistory.class));
            verify(rabbitTemplate).convertAndSend(eq(RabbitMqConfig.SWIFTDROP_EVENTS_TOPIC),
                    eq(RabbitMqConfig.ROUTING_KEY_STATUS_UPDATED), any(ParcelStatusUpdatedEvent.class));
            verify(eventPublisher).publishEvent(any(ParcelStatusChangedLocalEvent.class));
        }

        @Test
        @DisplayName("Successful transition to DELIVERED publishes both general and delivered routing keys")
        void successfulTransitionToDelivered() {
            Parcel parcel = buildTestParcel(parcelId, ParcelStatus.OUT_FOR_DELIVERY, courierId);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
            when(parcelStatusHistoryRepository.countByParcel_IdAndStatus(parcelId, ParcelStatus.FAILED)).thenReturn(0L);

            UpdateParcelStatusRequestDto req = UpdateParcelStatusRequestDto.builder()
                    .targetStatus(ParcelStatus.DELIVERED)
                    .recipientSignatureName("Nimal Silva")
                    .proofPhotoUrl("https://s3/proof1.png")
                    .deliveryNote("Handed to recipient personally")
                    .build();

            ParcelStatusUpdateResponseDto response = courierExecutionService.updateParcelStatus(courierId, parcelId, req);

            assertThat(response.getCurrentStatus()).isEqualTo(ParcelStatus.DELIVERED);

            // Verify both topic routing keys are invoked
            verify(rabbitTemplate).convertAndSend(eq(RabbitMqConfig.SWIFTDROP_EVENTS_TOPIC),
                    eq(RabbitMqConfig.ROUTING_KEY_STATUS_UPDATED), any(ParcelStatusUpdatedEvent.class));
            verify(rabbitTemplate).convertAndSend(eq(RabbitMqConfig.SWIFTDROP_EVENTS_TOPIC),
                    eq(RabbitMqConfig.ROUTING_KEY_STATUS_DELIVERED), any(ParcelStatusUpdatedEvent.class));
        }

        @Test
        @DisplayName("Successful transition to FAILED and RETURNED_TO_SENDER publishes failed routing key")
        void successfulTransitionToFailedAndReturnedToSender() {
            Parcel parcel = buildTestParcel(parcelId, ParcelStatus.OUT_FOR_DELIVERY, courierId);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
            when(parcelStatusHistoryRepository.countByParcel_IdAndStatus(parcelId, ParcelStatus.FAILED)).thenReturn(1L);

            UpdateParcelStatusRequestDto reqFailed = UpdateParcelStatusRequestDto.builder()
                    .targetStatus(ParcelStatus.FAILED)
                    .failureReason("CUSTOMER_UNAVAILABLE")
                    .build();

            courierExecutionService.updateParcelStatus(courierId, parcelId, reqFailed);

            verify(rabbitTemplate).convertAndSend(eq(RabbitMqConfig.SWIFTDROP_EVENTS_TOPIC),
                    eq(RabbitMqConfig.ROUTING_KEY_STATUS_FAILED), any(ParcelStatusUpdatedEvent.class));

            // Test RETURNED_TO_SENDER
            Parcel parcelFailed = buildTestParcel(parcelId, ParcelStatus.FAILED, courierId);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcelFailed));
            when(parcelStatusHistoryRepository.countByParcel_IdAndStatus(parcelId, ParcelStatus.FAILED)).thenReturn(3L);

            UpdateParcelStatusRequestDto reqReturned = UpdateParcelStatusRequestDto.builder()
                    .targetStatus(ParcelStatus.RETURNED_TO_SENDER)
                    .build();

            courierExecutionService.updateParcelStatus(courierId, parcelId, reqReturned);

            verify(rabbitTemplate, times(2)).convertAndSend(eq(RabbitMqConfig.SWIFTDROP_EVENTS_TOPIC),
                    eq(RabbitMqConfig.ROUTING_KEY_STATUS_FAILED), any(ParcelStatusUpdatedEvent.class));
        }

        @Test
        @DisplayName("RabbitMQ and WebSocket event failures are caught and handled gracefully (non-fatal)")
        void eventPublishingFailuresAreGraceful() {
            Parcel parcel = buildTestParcel(parcelId, ParcelStatus.ASSIGNED, courierId);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
            when(parcelStatusHistoryRepository.countByParcel_IdAndStatus(parcelId, ParcelStatus.FAILED)).thenReturn(0L);

            doThrow(new AmqpException("Broker down")).when(rabbitTemplate)
                    .convertAndSend(anyString(), anyString(), any(Object.class));
            doThrow(new RuntimeException("WebSocket publisher error")).when(eventPublisher)
                    .publishEvent(any(Object.class));

            UpdateParcelStatusRequestDto req = UpdateParcelStatusRequestDto.builder()
                    .targetStatus(ParcelStatus.PICKED_UP)
                    .build();

            ParcelStatusUpdateResponseDto response = courierExecutionService.updateParcelStatus(courierId, parcelId, req);

            assertThat(response).isNotNull();
            assertThat(response.getCurrentStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        }
    }
}
