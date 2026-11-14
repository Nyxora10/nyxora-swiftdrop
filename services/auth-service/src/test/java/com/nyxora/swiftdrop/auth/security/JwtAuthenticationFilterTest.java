package com.nyxora.swiftdrop.auth.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Unit Tests")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private FilterChain filterChain;

    @Mock
    private UserDetails userDetails;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(jwtTokenProvider, customUserDetailsService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Request without Authorization header continues filter chain without authenticating")
    void doFilter_NoAuthHeader_ChainContinues() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("Request with non-Bearer Authorization header continues filter chain without authenticating")
    void doFilter_NonBearerHeader_ChainContinues() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("Request with invalid JWT token continues filter chain without authenticating")
    void doFilter_InvalidToken_ChainContinues() throws ServletException, IOException {
        String token = "invalid-token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtTokenProvider, never()).getUserIdFromToken(anyString());
    }

    @Test
    @DisplayName("Valid JWT and enabled user sets Authentication in SecurityContext")
    void doFilter_ValidToken_UserEnabled_SetsAuthentication() throws ServletException, IOException {
        String token = "valid-token";
        UUID userId = UUID.randomUUID();
        request.addHeader("Authorization", "Bearer " + token);

        Claims claims = mock(Claims.class);
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getClaimsFromToken(token)).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class)).thenReturn(null);
        when(customUserDetailsService.loadUserById(userId)).thenReturn(userDetails);
        when(userDetails.isEnabled()).thenReturn(true);
        doReturn(Collections.emptyList()).when(userDetails).getAuthorities();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userDetails, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    @DisplayName("Valid JWT but disabled user does not set Authentication in SecurityContext")
    void doFilter_ValidToken_UserDisabled_DoesNotSetAuthentication() throws ServletException, IOException {
        String token = "valid-token";
        UUID userId = UUID.randomUUID();
        request.addHeader("Authorization", "Bearer " + token);

        Claims claims = mock(Claims.class);
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getClaimsFromToken(token)).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(customUserDetailsService.loadUserById(userId)).thenReturn(userDetails);
        when(userDetails.isEnabled()).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Exception during token extraction or loading is safely caught and chain proceeds")
    void doFilter_ExceptionThrown_HandledGracefully() throws ServletException, IOException {
        String token = "valid-token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenThrow(new RuntimeException("Database timeout"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}

