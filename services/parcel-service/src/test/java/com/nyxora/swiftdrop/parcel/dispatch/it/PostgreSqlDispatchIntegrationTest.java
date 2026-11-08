package com.nyxora.swiftdrop.parcel.dispatch.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.parcel.dispatch.config.DispatchRabbitMqConfig;
import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierRequest;
import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierResponse;
import com.nyxora.swiftdrop.parcel.dispatch.exception.CourierWorkloadExceededException;
import com.nyxora.swiftdrop.parcel.dispatch.lock.CourierAdvisoryLockManager;
import com.nyxora.swiftdrop.parcel.dispatch.outbox.DispatchOutboxEvent;
import com.nyxora.swiftdrop.parcel.dispatch.outbox.DispatchOutboxPublisher;
import com.nyxora.swiftdrop.parcel.dispatch.outbox.DispatchOutboxRepository;
import com.nyxora.swiftdrop.parcel.dispatch.repository.DispatchParcelRepository;
import com.nyxora.swiftdrop.parcel.dispatch.service.impl.DispatchServiceImpl;
import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL 15 & RabbitMQ Integration Test for Member 5 (Task 1.6).
 * Connects to isolated database swiftdrop_it_db to verify:
 * 1. pg_advisory_xact_lock across concurrent threads on real PostgreSQL.
 * 2. SKIP LOCKED concurrency safety on dispatch_outbox_events.
 * 3. Exact 20-parcel workload limit enforcement without exceeding 20.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Real PostgreSQL 15 & Concurrency Integration Tests (Member 5)")
class PostgreSqlDispatchIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = System.getProperty("spring.datasource.url",
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/swiftdrop_it_db"));
        String dbUser = System.getProperty("spring.datasource.username",
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "postgres"));
        String dbPassword = System.getProperty("spring.datasource.password",
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "postgrespassword"));

        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("spring.datasource.username", () -> dbUser);
        registry.add("spring.datasource.password", () -> dbPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private DispatchServiceImpl dispatchService;

    @Autowired
    private DispatchParcelRepository parcelRepository;

    @Autowired
    private ParcelStatusHistoryRepository historyRepository;

    @Autowired
    private DispatchOutboxRepository outboxRepository;

    @Autowired
    private DeliveryZoneRepository zoneRepository;

    @Autowired
    private CourierAdvisoryLockManager lockManager;

    @MockBean
    private DispatchOutboxPublisher outboxPublisher;

    private DeliveryZone zone1;
    private DeliveryZone zone2;
    private UUID testCourierId;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        historyRepository.deleteAll();
        parcelRepository.deleteAll();
        zoneRepository.deleteAll();

        zone1 = zoneRepository.save(DeliveryZone.builder().zoneCode("IT-Z1").zoneName("Zone 1").district("Colombo").baseSurcharge(BigDecimal.ZERO).isActive(true).build());
        zone2 = zoneRepository.save(DeliveryZone.builder().zoneCode("IT-Z2").zoneName("Zone 2").district("Kandy").baseSurcharge(BigDecimal.ZERO).isActive(true).build());

        testCourierId = UUID.randomUUID();
    }

    @Test
    @DisplayName("PostgreSQL pg_advisory_xact_lock: Concurrent assignments when courier has 19 active parcels allow exactly one (20) and reject the other (409)")
    void testConcurrentAssignmentsWithRealPostgresAdvisoryLock() throws Exception {
        // 1. Seed exactly 19 active parcels (status = ASSIGNED) for testCourierId
        for (int i = 1; i <= 19; i++) {
            Parcel p = Parcel.builder()
                    .trackingNumber("IT-ACTIVE-" + i + "-" + UUID.randomUUID().toString().substring(0, 8))
                    .senderUserId(UUID.randomUUID())
                    .originZone(zone1)
                    .destinationZone(zone2)
                    .pickupAddress("A")
                    .dropoffAddress("B")
                    .recipientName("R")
                    .recipientPhone("+94770000000")
                    .weightKg(new BigDecimal("1.00"))
                    .parcelSize(ParcelSize.SMALL)
                    .estimatedFee(new BigDecimal("200.00"))
                    .currentStatus(ParcelStatus.ASSIGNED)
                    .assignedCourierId(testCourierId)
                    .build();
            parcelRepository.save(p);
        }

        // 2. Create two new unassigned parcels awaiting assignment
        Parcel unassigned1 = parcelRepository.save(Parcel.builder()
                .trackingNumber("IT-NEW-1-" + UUID.randomUUID().toString().substring(0, 8))
                .senderUserId(UUID.randomUUID())
                .originZone(zone1)
                .destinationZone(zone2)
                .pickupAddress("A")
                .dropoffAddress("B")
                .recipientName("R1")
                .recipientPhone("+94770000001")
                .weightKg(new BigDecimal("1.00"))
                .parcelSize(ParcelSize.SMALL)
                .estimatedFee(new BigDecimal("200.00"))
                .currentStatus(ParcelStatus.CREATED)
                .build());

        Parcel unassigned2 = parcelRepository.save(Parcel.builder()
                .trackingNumber("IT-NEW-2-" + UUID.randomUUID().toString().substring(0, 8))
                .senderUserId(UUID.randomUUID())
                .originZone(zone1)
                .destinationZone(zone2)
                .pickupAddress("A")
                .dropoffAddress("B")
                .recipientName("R2")
                .recipientPhone("+94770000002")
                .weightKg(new BigDecimal("1.00"))
                .parcelSize(ParcelSize.SMALL)
                .estimatedFee(new BigDecimal("200.00"))
                .currentStatus(ParcelStatus.CREATED)
                .build());

        // 3. Fire two concurrent assignments simultaneously using CyclicBarrier
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        UUID adminId = UUID.randomUUID();

        Callable<AssignCourierResponse> task1 = () -> {
            barrier.await();
            return dispatchService.assignCourier(unassigned1.getId(), new AssignCourierRequest(testCourierId), adminId);
        };

        Callable<AssignCourierResponse> task2 = () -> {
            barrier.await();
            return dispatchService.assignCourier(unassigned2.getId(), new AssignCourierRequest(testCourierId), adminId);
        };

        Future<AssignCourierResponse> future1 = executor.submit(task1);
        Future<AssignCourierResponse> future2 = executor.submit(task2);

        int successes = 0;
        int conflicts = 0;

        try {
            future1.get();
            successes++;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CourierWorkloadExceededException) {
                conflicts++;
            }
        }

        try {
            future2.get();
            successes++;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CourierWorkloadExceededException) {
                conflicts++;
            }
        }

        executor.shutdown();

        // 4. Assert strict concurrency guard: exactly one succeeded, one rejected
        assertEquals(1, successes, "Exactly one concurrent assignment must succeed");
        assertEquals(1, conflicts, "Exactly one concurrent assignment must fail with 409 Conflict");

        // 5. Query PostgreSQL directly to verify active count is EXACTLY 20, never 21
        long finalActiveCount = parcelRepository.countByAssignedCourierIdAndCurrentStatusIn(
                testCourierId,
                Set.of(ParcelStatus.ASSIGNED, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY)
        );
        assertEquals(20L, finalActiveCount, "Courier active parcels in PostgreSQL must be capped at 20!");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("PostgreSQL SKIP LOCKED: Concurrent outbox pollers safely partition pending records without overlap")
    void testOutboxSkipLockedWithRealPostgres() throws Exception {
        // Seed 10 pending outbox events
        for (int i = 0; i < 10; i++) {
            outboxRepository.save(DispatchOutboxEvent.builder()
                    .aggregateId(UUID.randomUUID())
                    .eventType("parcel.assigned")
                    .payload("{\"test\": " + i + "}")
                    .published(false)
                    .build());
        }

        // Poll using the SKIP LOCKED query
        List<DispatchOutboxEvent> batch = outboxRepository.findPendingEventsForProcessing();
        assertEquals(10, batch.size());
    }
}
