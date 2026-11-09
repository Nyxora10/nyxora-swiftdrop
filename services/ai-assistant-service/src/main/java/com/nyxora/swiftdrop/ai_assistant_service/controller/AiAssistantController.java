package com.nyxora.swiftdrop.ai_assistant_service.controller;

import com.nyxora.swiftdrop.ai_assistant_service.client.GeminiClient;
import com.nyxora.swiftdrop.ai_assistant_service.dto.request.ChatRequest;
import com.nyxora.swiftdrop.ai_assistant_service.dto.request.GenerateRequest;
import com.nyxora.swiftdrop.ai_assistant_service.dto.response.ChatResponse;
import com.nyxora.swiftdrop.ai_assistant_service.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAssistantController {

    private final GeminiClient geminiClient;
    private final AiChatService aiChatService;

    public AiAssistantController(GeminiClient geminiClient, AiChatService aiChatService) {
        this.geminiClient = geminiClient;
        this.aiChatService = aiChatService;
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo() {
        return ResponseEntity.ok(Map.of(
                "service", "ai-assistant-service",
                "status", "UP",
                "model", geminiClient.getModel()
        ));
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(
            @Valid @RequestBody GenerateRequest request) {

        String response = geminiClient.generateContent(request.prompt());

        return ResponseEntity.ok(Map.of(
                "response", response,
                "model", geminiClient.getModel()
        ));
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request) {

        ChatResponse response = aiChatService.chat(request);

        return ResponseEntity.ok(response);
    }
}
