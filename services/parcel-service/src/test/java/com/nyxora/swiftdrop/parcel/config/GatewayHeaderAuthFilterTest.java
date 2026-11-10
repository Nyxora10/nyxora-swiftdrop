package com.nyxora.swiftdrop.parcel.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatewayHeaderAuthFilter gateway-trust verification")
class GatewayHeaderAuthFilterTest {

    private static final String SECRET = "gateway-secret-for-test";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Runs the filter and captures the authentication visible to the downstream chain. */
    private Authentication authenticationSeenDownstream(GatewayHeaderAuthFilter filter, MockHttpServletRequest request)
            throws Exception {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(SecurityContextHolder.getContext().getAuthentication());
            }
        };
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return seen.get();
    }

    private MockHttpServletRequest merchantRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/parcels/merchant/orders");
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-User-Role", "ROLE_MERCHANT");
        request.addHeader("X-Merchant-Id", UUID.randomUUID().toString());
        return request;
    }

    @Test
    @DisplayName("identity headers sent directly to the service (no gateway secret) are ignored")
    void directCallWithoutSecret_isNotAuthenticated() throws Exception {
        Authentication auth = authenticationSeenDownstream(new GatewayHeaderAuthFilter(SECRET), merchantRequest());
        assertThat(auth).isNull();
    }

    @Test
    @DisplayName("a wrong gateway secret is ignored")
    void wrongSecret_isNotAuthenticated() throws Exception {
        MockHttpServletRequest request = merchantRequest();
        request.addHeader("X-Gateway-Secret", "guessed");
        Authentication auth = authenticationSeenDownstream(new GatewayHeaderAuthFilter(SECRET), request);
        assertThat(auth).isNull();
    }

    @Test
    @DisplayName("gateway traffic with the correct secret is authenticated with the forwarded role")
    void correctSecret_isAuthenticated() throws Exception {
        MockHttpServletRequest request = merchantRequest();
        request.addHeader("X-Gateway-Secret", SECRET);
        Authentication auth = authenticationSeenDownstream(new GatewayHeaderAuthFilter(SECRET), request);
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MERCHANT");
    }
}
