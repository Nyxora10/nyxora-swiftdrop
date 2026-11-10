package com.nyxora.swiftdrop.parcel.dispatch.lock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourierAdvisoryLockManager Unit Tests")
class CourierAdvisoryLockManagerTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private CourierAdvisoryLockManager lockManager;

    @Test
    @DisplayName("acquireCourierLock() returns immediately when courierId is null")
    void acquireCourierLock_nullCourierId_returnsWithoutQuery() {
        assertDoesNotThrow(() -> lockManager.acquireCourierLock(null));
        verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName("acquireCourierLock() executes native query when courierId is present")
    void acquireCourierLock_validCourierId_executesQuery() {
        UUID courierId = UUID.randomUUID();
        when(entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:lockKey)")).thenReturn(query);
        when(query.setParameter(eq("lockKey"), anyLong())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(null);

        assertDoesNotThrow(() -> lockManager.acquireCourierLock(courierId));

        verify(entityManager).createNativeQuery("SELECT pg_advisory_xact_lock(:lockKey)");
        verify(query).setParameter(eq("lockKey"), anyLong());
        verify(query).getSingleResult();
    }

    @Test
    @DisplayName("acquireCourierLock() catches exceptions gracefully on non-PostgreSQL dialects")
    void acquireCourierLock_queryThrowsException_caughtGracefully() {
        UUID courierId = UUID.randomUUID();
        when(entityManager.createNativeQuery(anyString())).thenThrow(new RuntimeException("Function pg_advisory_xact_lock not found"));

        assertDoesNotThrow(() -> lockManager.acquireCourierLock(courierId));
    }
}

