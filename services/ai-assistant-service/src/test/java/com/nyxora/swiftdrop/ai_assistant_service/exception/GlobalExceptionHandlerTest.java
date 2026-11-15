package com.nyxora.swiftdrop.ai_assistant_service.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("Should handle GeminiServiceException with 503 SERVICE_UNAVAILABLE")
    void shouldHandleGeminiServiceException() {
        GeminiServiceException ex = new GeminiServiceException("AI service unavailable");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleGeminiError(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("AI Service Unavailable");
        assertThat(response.getBody().getDetail()).contains("The AI assistant is temporarily unavailable");
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException with 400 BAD_REQUEST")
    void shouldHandleMethodArgumentNotValidException() throws NoSuchMethodException {
        var method = this.getClass().getDeclaredMethod("setUp");
        MethodParameter parameter = new MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                parameter,
                new BeanPropertyBindingResult(new Object(), "target")
        );

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleValidationError(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Invalid Request");
        assertThat(response.getBody().getDetail()).contains("Please provide a valid prompt");
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException with 400 BAD_REQUEST")
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Bad argument");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleInvalidArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Invalid Request");
        assertThat(response.getBody().getDetail()).isEqualTo("The supplied request is invalid.");
    }

    @Test
    @DisplayName("Should handle unexpected generic Exception with 500 INTERNAL_SERVER_ERROR")
    void shouldHandleGenericException() {
        Exception ex = new RuntimeException("Unexpected internal error");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleUnexpectedError(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getDetail()).contains("An unexpected error occurred");
    }
}
