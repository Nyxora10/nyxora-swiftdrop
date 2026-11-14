package com.nyxora.swiftdrop.ai_assistant_service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.ai_assistant_service.exception.GeminiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiClientTest {

    private MockRestServiceServer mockServer;
    private GeminiClient geminiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_KEY = "test-gemini-key";
    private static final String MODEL = "gemini-2.0-flash";
    private static final String BASE_URL = "http://localhost:8090";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        geminiClient = new GeminiClient(API_KEY, MODEL, builder.build(), objectMapper);
    }

    @Test
    @DisplayName("Should successfully generate content from Gemini API with prompt only")
    void shouldGenerateContentSuccessfully() {
        String mockResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "SwiftDrop delivers parcels islandwide across Sri Lanka." }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.0-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", API_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Tell me about SwiftDrop"))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        String result = geminiClient.generateContent("Tell me about SwiftDrop");

        assertThat(result).isEqualTo("SwiftDrop delivers parcels islandwide across Sri Lanka.");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should include system instruction in request body when provided")
    void shouldIncludeSystemInstructionWhenProvided() {
        String mockResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Pricing begins at 350 LKR." }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.0-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.system_instruction.parts[0].text").value("You are a helpful logistics assistant."))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("How much does delivery cost?"))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        String result = geminiClient.generateContent(
                "You are a helpful logistics assistant.",
                "How much does delivery cost?"
        );

        assertThat(result).isEqualTo("Pricing begins at 350 LKR.");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should support fluent PromptBuilder and CallBuilder syntax")
    void shouldSupportFluentPromptBuilder() {
        String mockResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Tracking number SD-20261107-7K9P2X is currently IN_TRANSIT." }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.0-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.system_instruction.parts[0].text").value("Track parcel system prompt"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Where is my parcel?"))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        String result = geminiClient.prompt()
                .system("Track parcel system prompt")
                .user("Where is my parcel?")
                .call()
                .content();

        assertThat(result).isEqualTo("Tracking number SD-20261107-7K9P2X is currently IN_TRANSIT.");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw GeminiServiceException when API key is null, blank, or placeholder")
    void shouldThrowExceptionWhenApiKeyInvalid() {
        RestClient restClient = RestClient.builder().baseUrl(BASE_URL).build();

        GeminiClient nullKeyClient = new GeminiClient(null, MODEL, restClient, objectMapper);
        assertThatThrownBy(() -> nullKeyClient.generateContent("hello"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("AI provider is not configured");

        GeminiClient blankKeyClient = new GeminiClient("   ", MODEL, restClient, objectMapper);
        assertThatThrownBy(() -> blankKeyClient.generateContent("hello"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("AI provider is not configured");

        GeminiClient placeholderKeyClient = new GeminiClient("your_gemini_api_key_here", MODEL, restClient, objectMapper);
        assertThatThrownBy(() -> placeholderKeyClient.generateContent("hello"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("AI provider is not configured");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when prompt is null or blank")
    void shouldThrowExceptionWhenPromptNullOrBlank() {
        assertThatThrownBy(() -> geminiClient.generateContent(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt must not be empty");

        assertThatThrownBy(() -> geminiClient.generateContent("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt must not be empty");
    }

    @Test
    @DisplayName("Should throw GeminiServiceException when Gemini API returns 4xx or 5xx HTTP status")
    void shouldHandleRestClientResponseException() {
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.0-flash:generateContent"))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> geminiClient.generateContent("Test prompt"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("AI provider request failed");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw GeminiServiceException when Gemini API returns 500 server error")
    void shouldHandleServerError() {
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.0-flash:generateContent"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> geminiClient.generateContent("Test prompt"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("AI provider request failed");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw GeminiServiceException when candidates array is missing or empty")
    void shouldHandleEmptyCandidatesResponse() {
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.0-flash:generateContent"))
                .andRespond(withSuccess("{\"candidates\": []}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiClient.generateContent("Hello"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("AI provider returned empty response");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw GeminiServiceException when candidates node is missing from response")
    void shouldHandleMissingCandidatesResponse() {
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.0-flash:generateContent"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiClient.generateContent("Hello"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("AI provider returned empty response");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw GeminiServiceException when content parts are missing or empty")
    void shouldHandleMalformedPartsResponse() {
        String malformedResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": []
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.0-flash:generateContent"))
                .andRespond(withSuccess(malformedResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiClient.generateContent("Hello"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("AI provider returned malformed response");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw GeminiServiceException when candidate content parts is missing")
    void shouldHandleMissingPartsResponse() {
        String missingPartsResponse = """
                {
                  "candidates": [
                    {
                      "content": {}
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.0-flash:generateContent"))
                .andRespond(withSuccess(missingPartsResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiClient.generateContent("Hello"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("AI provider returned malformed response");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw GeminiServiceException when response text is blank")
    void shouldHandleBlankTextResponse() {
        String blankTextResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "    " }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.0-flash:generateContent"))
                .andRespond(withSuccess(blankTextResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiClient.generateContent("Hello"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("AI provider returned blank response");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw GeminiServiceException when response is invalid JSON")
    void shouldHandleInvalidJsonResponse() {
        mockServer.expect(requestTo(BASE_URL + "/models/gemini-2.0-flash:generateContent"))
                .andRespond(withSuccess("Not Valid JSON {{", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiClient.generateContent("Hello"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("Failed to process AI provider response");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should return configured model name")
    void shouldReturnConfiguredModel() {
        assertThat(geminiClient.getModel()).isEqualTo(MODEL);
    }

    @Test
    @DisplayName("Should initialize properly using standard constructor")
    void shouldInitializeWithStandardConstructor() {
        GeminiClient standardClient = new GeminiClient(
                "my-key",
                "gemini-1.5-pro",
                "http://localhost:8090",
                objectMapper
        );
        assertThat(standardClient.getModel()).isEqualTo("gemini-1.5-pro");
    }
}
