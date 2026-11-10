package com.nyxora.swiftdrop.ai_assistant_service.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class McpSecurityFilterTest {

    // 256-bit Base64-encoded secret key
    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private McpSecurityFilter securityFilter;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        securityFilter = new McpSecurityFilter(TEST_SECRET);
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("Should bypass non-MCP endpoints without authentication")
    void shouldBypassNonMcpEndpoints() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ai/info");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        securityFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when MCP endpoint is called without credentials")
    void shouldRejectUnauthenticatedMcpRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        securityFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Unauthorized MCP access");
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when MCP endpoint is called with invalid JWT")
    void shouldRejectInvalidJwt() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", "Bearer invalid.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        securityFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Unauthorized MCP access");
    }

    @Test
    @DisplayName("Should authenticate and populate UserContextHolder when valid JWT is provided")
    void shouldAuthenticateValidJwt() throws ServletException, IOException {
        String token = Jwts.builder()
                .subject("user-999")
                .claim("role", "CUSTOMER")
                .claim("email", "customer@swiftdrop.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key)
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<UserIdentity> capturedIdentity = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> capturedIdentity.set(UserContextHolder.getContext());

        securityFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(capturedIdentity.get()).isNotNull();
        assertThat(capturedIdentity.get().userId()).isEqualTo("user-999");
        assertThat(capturedIdentity.get().role()).isEqualTo("CUSTOMER");
        assertThat(capturedIdentity.get().email()).isEqualTo("customer@swiftdrop.com");

        // Verify context is cleared after request completes
        assertThat(UserContextHolder.getContext()).isNull();
    }

    @Test
    @DisplayName("Should authenticate and populate UserContextHolder when trusted gateway headers are provided")
    void shouldAuthenticateTrustedGatewayHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/message");
        request.addHeader("X-User-Id", "dispatcher-42");
        request.addHeader("X-User-Role", "DISPATCHER");
        request.addHeader("X-User-Email", "dispatcher@swiftdrop.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<UserIdentity> capturedIdentity = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> capturedIdentity.set(UserContextHolder.getContext());

        securityFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(capturedIdentity.get()).isNotNull();
        assertThat(capturedIdentity.get().userId()).isEqualTo("dispatcher-42");
        assertThat(capturedIdentity.get().role()).isEqualTo("DISPATCHER");

        // Verify context is cleared
        assertThat(UserContextHolder.getContext()).isNull();
    }
}
