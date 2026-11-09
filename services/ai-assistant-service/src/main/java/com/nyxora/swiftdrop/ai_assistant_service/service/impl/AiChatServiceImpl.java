package com.nyxora.swiftdrop.ai_assistant_service.service.impl;

import com.nyxora.swiftdrop.ai_assistant_service.dto.request.ChatRequest;
import com.nyxora.swiftdrop.ai_assistant_service.dto.response.ChatResponse;
import com.nyxora.swiftdrop.ai_assistant_service.service.AiChatService;
import com.nyxora.swiftdrop.ai_assistant_service.tool.ParcelTrackingTool;
import com.nyxora.swiftdrop.ai_assistant_service.tool.RateQuoteTool;
import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger log =
            LoggerFactory.getLogger(AiChatServiceImpl.class);

    private final ChatClient chatClient;

    public AiChatServiceImpl(
            ChatClient.Builder chatClientBuilder,
            ChatMemory chatMemory,
            ParcelTrackingTool parcelTrackingTool,
            RateQuoteTool rateQuoteTool) {

        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(parcelTrackingTool, rateQuoteTool)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String conversationId = (request.conversationId() != null && !request.conversationId().isBlank())
                ? request.conversationId().trim()
                : UUID.randomUUID().toString();

        String reply = chat(request.message(), conversationId);
        return new ChatResponse(reply, conversationId);
    }

    @Override
    public String chat(String message) {
        return chat(message, UUID.randomUUID().toString());
    }

    @Override
    public String chat(String message, String conversationId) {

        log.info("Processing chat request for conversationId '{}' with message: '{}'",
                conversationId, PiiMaskingUtil.maskText(message));

        return chatClient.prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
