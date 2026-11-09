package com.nyxora.swiftdrop.notification.webhook.dto;

import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Standard paginated envelope response for webhook delivery logs queries (Task 3.6).
 *
 * @param content       List of delivery log items on current page
 * @param page          Current zero-indexed page number
 * @param size          Configured page size
 * @param totalElements Total count of matching logs across all pages
 * @param totalPages    Total number of available pages
 * @param isFirst       Whether this is the first page
 * @param isLast        Whether this is the final page
 */
public record WebhookLogsPageResponse(
        List<WebhookDeliveryLogResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean isFirst,
        boolean isLast
) {
    /**
     * Constructs a paginated response wrapper from a Spring Data Page.
     *
     * @param page Spring Data page of JPA entities
     * @return initialized paginated response
     */
    public static WebhookLogsPageResponse fromPage(Page<WebhookDeliveryLog> page) {
        if (page == null) {
            return new WebhookLogsPageResponse(List.of(), 0, 0, 0, 0, true, true);
        }
        List<WebhookDeliveryLogResponse> dtoList = page.getContent().stream()
                .map(WebhookDeliveryLogResponse::fromEntity)
                .toList();

        return new WebhookLogsPageResponse(
                dtoList,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}

