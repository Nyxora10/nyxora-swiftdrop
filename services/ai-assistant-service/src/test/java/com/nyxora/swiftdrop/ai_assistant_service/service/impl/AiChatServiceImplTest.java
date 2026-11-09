package com.nyxora.swiftdrop.ai_assistant_service.service.impl;

import com.nyxora.swiftdrop.ai_assistant_service.config.AiMemoryConfig;
import com.nyxora.swiftdrop.ai_assistant_service.dto.request.ChatRequest;
import com.nyxora.swiftdrop.ai_assistant_service.dto.response.ChatResponse;
import com.nyxora.swiftdrop.ai_assistant_service.tool.ParcelTrackingTool;
import com.nyxora.swiftdrop.ai_assistant_service.tool.RateQuoteTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiChatServiceImplTest {

    private AiChatServiceImpl aiChatService;
    private ChatModel chatModel;
    private ChatMemory chatMemory;
    private ParcelTrackingTool parcelTrackingTool;
    private RateQuoteTool rateQuoteTool;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        ChatClient.Builder builder = ChatClient.builder(chatModel);

        AiMemoryConfig config = new AiMemoryConfig();
        ReflectionTestUtils.setField(config, "maxMessages", 20);
        ChatMemoryRepository repository = config.chatMemoryRepository();
        chatMemory = config.chatMemory(repository);

        parcelTrackingTool = new ParcelTrackingTool();
        rateQuoteTool = new RateQuoteTool();

        aiChatService = new AiChatServiceImpl(builder, chatMemory, parcelTrackingTool, rateQuoteTool);
    }

    @Test
    @DisplayName("Should generate a new conversationId when none is provided")
    void shouldGenerateConversationIdWhenMissing() {
        org.springframework.ai.chat.model.ChatResponse mockModelResponse =
                new org.springframework.ai.chat.model.ChatResponse(
                        List.of(new Generation(new AssistantMessage("To calculate a rate quote, please provide weight and cities.")))
                );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockModelResponse);

        ChatRequest request = new ChatRequest("How much does delivery cost?");
        ChatResponse response = aiChatService.chat(request);

        assertThat(response.conversationId()).isNotBlank();
        assertThat(response.reply()).contains("To calculate a rate quote");
    }

    @Test
    @DisplayName("Should preserve existing conversationId when provided")
    void shouldPreserveProvidedConversationId() {
        org.springframework.ai.chat.model.ChatResponse mockModelResponse =
                new org.springframework.ai.chat.model.ChatResponse(
                        List.of(new Generation(new AssistantMessage("Please provide your tracking number.")))
                );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockModelResponse);

        String existingId = "custom-conversation-id-123";
        ChatRequest request = new ChatRequest("Where is my parcel?", existingId);
        ChatResponse response = aiChatService.chat(request);

        assertThat(response.conversationId()).isEqualTo(existingId);
        assertThat(response.reply()).contains("Please provide your tracking number.");
    }

    @Test
    @DisplayName("Should call model with conversationId advisors and store history")
    void shouldCallModelAndRetainHistory() {
        org.springframework.ai.chat.model.ChatResponse mockModelResponse =
                new org.springframework.ai.chat.model.ChatResponse(
                        List.of(new Generation(new AssistantMessage("I can assist with SwiftDrop deliveries.")))
                );

        when(chatModel.call(any(Prompt.class))).thenReturn(mockModelResponse);

        String conversationId = "active-conv-777";
        ChatRequest request = new ChatRequest("Hello assistant!", conversationId);
        ChatResponse response = aiChatService.chat(request);

        assertThat(response.reply()).isEqualTo("I can assist with SwiftDrop deliveries.");
        assertThat(response.conversationId()).isEqualTo(conversationId);

        // Verify history was saved into ChatMemory
        assertThat(chatMemory.get(conversationId)).isNotEmpty();
    }
}
