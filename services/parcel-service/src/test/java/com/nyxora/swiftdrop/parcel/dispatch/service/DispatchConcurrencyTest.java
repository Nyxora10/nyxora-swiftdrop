package com.nyxora.swiftdrop.parcel.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierRequest;
import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierResponse;
import com.nyxora.swiftdrop.parcel.dispatch.exception.CourierWorkloadExceededException;
import com.nyxora.swiftdrop.parcel.dispatch.lock.CourierAdvisoryLockManager;
import com.nyxora.swiftdrop.parcel.dispatch.outbox.DispatchOutboxEvent;
import com.nyxora.swiftdrop.parcel.dispatch.outbox.DispatchOutboxPublisher;
import com.nyxora.swiftdrop.parcel.dispatch.outbox.DispatchOutboxRepository;
import com.nyxora.swiftdrop.parcel.dispatch.repository.DispatchParcelRepository;
import com.nyxora.swiftdrop.parcel.dispatch.service.impl.DispatchServiceImpl;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dispatch Concurrency & Workload Guard Tests (Member 5: Task 1.6)")
class DispatchConcurrencyTest {

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

    private UUID courierId;

    @BeforeEach
    void setUp() {
        courierId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Concurrency Guard: Two simultaneous assignment requests when courier is at 19 active parcels allow exactly one and reject the other")
    void concurrentAssignment_whenActiveCountIs19_allowsExactlyOneAndRejectsSecond() throws InterruptedException, ExecutionException {
        UUID parcelId1 = UUID.randomUUID();
        UUID parcelId2 = UUID.randomUUID();

        Parcel parcel1 = Parcel.builder().id(parcelId1).trackingNumber("SD-1").currentStatus(ParcelStatus.CREATED).build();
        Parcel parcel2 = Parcel.builder().id(parcelId2).trackingNumber("SD-2").currentStatus(ParcelStatus.CREATED).build();

        when(dispatchParcelRepository.findById(parcelId1)).thenReturn(Optional.of(parcel1));
        when(dispatchParcelRepository.findById(parcelId2)).thenReturn(Optional.of(parcel2));

        // Simulate state transition: first call sees 19, second call sees 20
        AtomicInteger activeCount = new AtomicInteger(19);
        when(dispatchParcelRepository.countByAssignedCourierIdAndCurrentStatusIn(eq(courierId), any(Collection.class)))
                .thenAnswer(inv -> (long) activeCount.getAndIncrement());

        when(dispatchParcelRepository.save(any(Parcel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(DispatchOutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<AssignCourierResponse> task1 = () -> {
            barrier.await();
            return dispatchService.assignCourier(parcelId1, new AssignCourierRequest(courierId), UUID.randomUUID());
        };

        Callable<AssignCourierResponse> task2 = () -> {
            barrier.await();
            return dispatchService.assignCourier(parcelId2, new AssignCourierRequest(courierId), UUID.randomUUID());
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

        // Exactly one should succeed and one should fail with 409 Conflict
        assertEquals(1, successes, "Exactly one concurrent assignment must succeed");
        assertEquals(1, conflicts, "Exactly one concurrent assignment must be rejected with 409 Conflict");

        // Verify advisory lock was invoked for both requests to serialize them
        verify(courierAdvisoryLockManager, times(2)).acquireCourierLock(courierId);
    }
}
