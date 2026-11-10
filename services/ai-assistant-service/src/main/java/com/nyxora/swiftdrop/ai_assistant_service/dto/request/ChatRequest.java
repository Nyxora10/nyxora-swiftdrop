package com.nyxora.swiftdrop.ai_assistant_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(

        @NotBlank(message = "Message is required")
        @Size(max = 2000,
            message = "Message must not exceed 2000 characters")
        String message,

        String conversationId

) {
    public ChatRequest(String message) {
        this(message, null);
    }
}
