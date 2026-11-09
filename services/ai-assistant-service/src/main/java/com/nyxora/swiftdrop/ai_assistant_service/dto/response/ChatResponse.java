package com.nyxora.swiftdrop.ai_assistant_service.dto.response;

public record ChatResponse(
        String reply,
        String conversationId
) {
    public ChatResponse(String reply) {
        this(reply, null);
    }
}
