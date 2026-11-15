package com.nyxora.swiftdrop.ai_assistant_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.ai_assistant_service.client.GeminiClient;
import com.nyxora.swiftdrop.ai_assistant_service.dto.request.ChatRequest;
import com.nyxora.swiftdrop.ai_assistant_service.dto.request.GenerateRequest;
import com.nyxora.swiftdrop.ai_assistant_service.dto.response.ChatResponse;
import com.nyxora.swiftdrop.ai_assistant_service.service.AiChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiAssistantControllerTest {

    private MockMvc mockMvc;
    private GeminiClient geminiClient;
    private AiChatService aiChatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        geminiClient = mock(GeminiClient.class);
        aiChatService = mock(AiChatService.class);
        AiAssistantController controller = new AiAssistantController(geminiClient, aiChatService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Should return service info and active AI model")
    void shouldReturnServiceInfo() throws Exception {
        when(geminiClient.getModel()).thenReturn("gemini-2.0-flash");

        mockMvc.perform(get("/api/v1/ai/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("ai-assistant-service"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.model").value("gemini-2.0-flash"));
    }

    @Test
    @DisplayName("Should generate completion response using GeminiClient")
    void shouldGenerateContent() throws Exception {
        when(geminiClient.generateContent("What is SwiftDrop?"))
                .thenReturn("SwiftDrop is an enterprise courier service.");
        when(geminiClient.getModel()).thenReturn("gemini-2.0-flash");

        GenerateRequest request = new GenerateRequest("What is SwiftDrop?");

        mockMvc.perform(post("/api/v1/ai/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("SwiftDrop is an enterprise courier service."))
                .andExpect(jsonPath("$.model").value("gemini-2.0-flash"));
    }

    @Test
    @DisplayName("Should return chat response using AiChatService")
    void shouldChatSuccessfully() throws Exception {
        ChatResponse expectedResponse = new ChatResponse(
                "Your parcel SD-123456 is on the way.",
                "conv-abc-123"
        );
        when(aiChatService.chat(any(ChatRequest.class))).thenReturn(expectedResponse);

        ChatRequest request = new ChatRequest("Track SD-123456", "conv-abc-123");

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Your parcel SD-123456 is on the way."))
                .andExpect(jsonPath("$.conversationId").value("conv-abc-123"));
    }
}
