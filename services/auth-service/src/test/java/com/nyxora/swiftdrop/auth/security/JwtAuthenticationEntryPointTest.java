package com.nyxora.swiftdrop.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtAuthenticationEntryPoint Unit Tests")
class JwtAuthenticationEntryPointTest {

    private ObjectMapper objectMapper;
    private JwtAuthenticationEntryPoint entryPoint;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        entryPoint = new JwtAuthenticationEntryPoint(objectMapper);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/secure/resource");
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("Commence writes HTTP 401 ProblemDetail JSON with exception message")
    void commence_WithExceptionMessage_WritesProblemDetail() throws IOException {
        InsufficientAuthenticationException ex =
                new InsufficientAuthenticationException("Full authentication is required to access this resource.");

        entryPoint.commence(request, response, ex);

        assertEquals(401, response.getStatus());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, response.getContentType());

        String json = response.getContentAsString();
        assertTrue(json.contains("https://swiftdrop.lk/errors/unauthorized"));
        assertTrue(json.contains("Unauthorized"));
        assertTrue(json.contains("Full authentication is required to access this resource."));
        assertTrue(json.contains("/api/v1/secure/resource"));
    }

    @Test
    @DisplayName("Commence with null message falls back to default detail message")
    void commence_WithNullMessage_FallsBackToDefault() throws IOException {
        BadCredentialsException exNull = new BadCredentialsException(null);

        entryPoint.commence(request, response, exNull);

        assertEquals(401, response.getStatus());
        String json = response.getContentAsString();
        assertTrue(json.contains("Full authentication is required to access this resource."));
    }
}

