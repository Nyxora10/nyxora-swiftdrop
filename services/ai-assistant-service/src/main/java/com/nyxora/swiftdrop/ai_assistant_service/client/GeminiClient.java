package com.nyxora.swiftdrop.ai_assistant_service.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.ai_assistant_service.exception.GeminiServiceException;
import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class GeminiClient {

    private static final Logger log =
            LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiClient(
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.model:gemini-2.0-flash}") String model,
            @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}")
            String baseUrl,
            ObjectMapper objectMapper) {

        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(30000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        log.info("Gemini client initialized. Model: {}", model);
    }

    /**
     * Fluent builder for prompts (compatible with ChatClient prompt syntax).
     */
    public PromptBuilder prompt() {
        return new PromptBuilder();
    }

    public class PromptBuilder {
        private String systemInstruction;
        private String userMessage;

        public PromptBuilder system(String systemInstruction) {
            this.systemInstruction = systemInstruction;
            return this;
        }

        public PromptBuilder user(String userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        public CallBuilder call() {
            return new CallBuilder(systemInstruction, userMessage);
        }
    }

    public class CallBuilder {
        private final String systemInstruction;
        private final String userMessage;

        public CallBuilder(String systemInstruction, String userMessage) {
            this.systemInstruction = systemInstruction;
            this.userMessage = userMessage;
        }

        public String content() {
            return generateContent(systemInstruction, userMessage);
        }
    }

    public String generateContent(String prompt) {
        return generateContent(null, prompt);
    }

    public String generateContent(String systemInstruction, String prompt) {

        if (apiKey == null || apiKey.isBlank()
                || "your_gemini_api_key_here".equalsIgnoreCase(apiKey)) {

            log.error("Gemini API key is not configured");

            throw new GeminiServiceException(
                    "AI provider is not configured"
            );
        }

        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "Prompt must not be empty"
            );
        }

        log.info("Sending request to Gemini model '{}' with prompt: '{}'",
                model, PiiMaskingUtil.maskText(prompt));

        Map<String, Object> requestBody;
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            requestBody = Map.of(
                    "system_instruction", Map.of(
                            "parts", List.of(Map.of("text", systemInstruction))
                    ),
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", prompt))
                            )
                    )
            );
        } else {
            requestBody = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", prompt))
                            )
                    )
            );
        }

        try {
            String rawResponse = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return extractTextFromResponse(rawResponse);

        } catch (RestClientResponseException ex) {

            // Do not log provider response bodies or secrets.
            log.error(
                    "Gemini API returned HTTP status {}",
                    ex.getStatusCode().value()
            );

            throw new GeminiServiceException(
                    "AI provider request failed"
            );

        } catch (RestClientException ex) {

            log.error(
                    "Gemini API communication failed: {}",
                    ex.getClass().getSimpleName()
            );

            throw new GeminiServiceException(
                    "AI service temporarily unavailable"
            );
        }
    }

    private String extractTextFromResponse(String rawResponse) {

        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            JsonNode candidates = root.path("candidates");

            if (candidates.isMissingNode()
                    || !candidates.isArray()
                    || candidates.isEmpty()) {

                log.error("Gemini API response contained no candidates");

                throw new GeminiServiceException(
                        "AI provider returned empty response"
                );
            }

            JsonNode firstCandidate = candidates.get(0);

            JsonNode parts = firstCandidate
                    .path("content")
                    .path("parts");

            if (parts.isMissingNode()
                    || !parts.isArray()
                    || parts.isEmpty()) {

                log.error("Gemini candidate content contained no parts");

                throw new GeminiServiceException(
                        "AI provider returned malformed response"
                );
            }

            String text = parts.get(0).path("text").asText();

            if (text.isBlank()) {

                log.error("Gemini response text part is blank");

                throw new GeminiServiceException(
                        "AI provider returned blank response"
                );
            }

            return text;

        } catch (IOException ex) {

            log.error("Failed to parse Gemini response JSON");

            throw new GeminiServiceException(
                    "Failed to process AI provider response"
            );
        }
    }

    public String getModel() {
        return model;
    }
}
