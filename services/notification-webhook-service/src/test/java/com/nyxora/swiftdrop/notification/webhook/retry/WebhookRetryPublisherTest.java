package com.nyxora.swiftdrop.notification.webhook.retry;

import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.repository.WebhookDeliveryLogRepository;
import com.nyxora.swiftdrop.notification.webhook.retry.dto.WebhookRetryMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessagePostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookRetryPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private WebhookDeliveryLogRepository repository;

    private WebhookRetryPublisher publisher;
    private WebhookDeliveryLog log;

    @BeforeEach
    void setUp() {
        publisher = new WebhookRetryPublisher(rabbitTemplate, repository);
        log = new WebhookDeliveryLog();
        log.setId(UUID.randomUUID());
        log.setMerchantId(UUID.randomUUID());
        log.setParcelId(UUID.randomUUID());
        log.setEventType("parcel.updated");
        log.setPayload("{}");
        log.setRetryCount(0);
    }

    @Test
    void schedulesRetryAndUpdatesAuditLogAfterPublish() {
        assertThat(publisher.scheduleRetry(log)).isTrue();

        assertThat(log.getRetryCount()).isEqualTo(1);
        assertThat(log.getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.RETRYING);
        verify(repository).save(log);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(WebhookRetryMessage.class),
                any(MessagePostProcessor.class));
    }

    @Test
    void marksExhaustedRetryAsDeadLetterWithoutPublishing() {
        log.setRetryCount(WebhookRetryPolicy.MAX_RETRIES);

        assertThat(publisher.scheduleRetry(log)).isFalse();

        assertThat(log.getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.DEAD_LETTER);
        verify(repository).save(log);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void doesNotPersistAttemptWhenBrokerPublishFails() {
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(WebhookRetryMessage.class),
                        any(MessagePostProcessor.class));

        assertThatThrownBy(() -> publisher.scheduleRetry(log))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publication failed");

        verify(repository, never()).save(any());
    }

    @Test
    void publishesManualRetry() {
        assertThat(publisher.publishManualRetry(log)).isTrue();

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(WebhookRetryMessage.class),
                any(MessagePostProcessor.class));
        verifyNoInteractions(repository);
    }

    @Test
    void publishesManualRetryUsingMaximumAttemptWhenCountIsNull() {
        log.setRetryCount(null);

        assertThat(publisher.publishManualRetry(log)).isTrue();

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(WebhookRetryMessage.class),
                any(MessagePostProcessor.class));
    }

    @Test
    void reportsManualRetryBrokerFailure() {
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(WebhookRetryMessage.class),
                        any(MessagePostProcessor.class));

        assertThatThrownBy(() -> publisher.publishManualRetry(log))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manual retry publication failed");
    }

    @Test
    void rejectsUnpersistedLog() {
        assertThatThrownBy(() -> publisher.scheduleRetry(new WebhookDeliveryLog()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.publishManualRetry(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsNullRetryCountAsFirstAttempt() {
        log.setRetryCount(null);

        assertThat(publisher.scheduleRetry(log)).isTrue();

        assertThat(log.getRetryCount()).isEqualTo(1);
    }
}
