package com.nyxora.swiftdrop.auth.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InternalApiKeyFilter Unit Tests")
class InternalApiKeyFilterTest {

    private static final String INTERNAL_URI = InternalApiKeyFilter.INTERNAL_PATH_PREFIX + "merchants/abc/webhook-config";

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Runs the filter and returns the authentication visible to the next element in the chain. */
    private Authentication runFilter(InternalApiKeyFilter filter, String uri, String headerValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (headerValue != null) {
            request.addHeader(InternalApiKeyFilter.HEADER, headerValue);
        }
        AtomicReference<Authentication> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication());
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return seen.get();
    }

    @Test
    @DisplayName("a matching key authenticates the request as the internal service")
    void validKey_grantsInternalServiceRole() throws Exception {
        Authentication auth = runFilter(new InternalApiKeyFilter("shared-key"), INTERNAL_URI, " shared-key ");

        assertNotNull(auth);
        assertEquals("internal-service", auth.getPrincipal());
        assertEquals(List.of(InternalApiKeyFilter.ROLE_INTERNAL_SERVICE),
                auth.getAuthorities().stream().map(Object::toString).toList());
    }

    @Test
    @DisplayName("a wrong key leaves the request unauthenticated")
    void wrongKey_isRejected() throws Exception {
        assertNull(runFilter(new InternalApiKeyFilter("shared-key"), INTERNAL_URI, "other-key"));
    }

    @Test
    @DisplayName("a missing header leaves the request unauthenticated")
    void missingHeader_isRejected() throws Exception {
        assertNull(runFilter(new InternalApiKeyFilter("shared-key"), INTERNAL_URI, null));
    }

    @Test
    @DisplayName("fails closed when no key is configured (null or blank)")
    void noConfiguredKey_rejectsEverything() throws Exception {
        assertNull(runFilter(new InternalApiKeyFilter(null), INTERNAL_URI, ""));
        assertNull(runFilter(new InternalApiKeyFilter("   "), INTERNAL_URI, "   "));
    }

    @Test
    @DisplayName("a user JWT set by an earlier filter never authorises an internal route")
    void existingUserAuthentication_isCleared() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("merchant-user", null, List.of()));

        assertNull(runFilter(new InternalApiKeyFilter("shared-key"), INTERNAL_URI, null));
    }

    @Test
    @DisplayName("non-internal routes pass through untouched")
    void nonInternalRoute_isNotFiltered() throws Exception {
        Authentication existing = new UsernamePasswordAuthenticationToken("merchant-user", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);

        assertSame(existing, runFilter(new InternalApiKeyFilter("shared-key"), "/api/v1/auth/merchant/profile", null));
    }
}
