package com.nyxora.swiftdrop.auth.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/test");
    }

    @Test
    @DisplayName("EmailAlreadyExistsException returns HTTP 409 Conflict ProblemDetail")
    void handleEmailAlreadyExists() {
        EmailAlreadyExistsException ex = new EmailAlreadyExistsException("Email already registered: user@swiftdrop.lk");
        ProblemDetail problem = handler.handleEmailAlreadyExists(ex, request);

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Email Already Registered", problem.getTitle());
        assertEquals(URI.create("https://swiftdrop.lk/errors/email-already-exists"), problem.getType());
        assertEquals(URI.create("/api/v1/auth/test"), problem.getInstance());
        assertNotNull(problem.getProperties().get("timestamp"));
    }

    @Test
    @DisplayName("AccountPendingApprovalException returns HTTP 403 Forbidden ProblemDetail")
    void handleAccountPendingApproval() {
        AccountPendingApprovalException ex = new AccountPendingApprovalException("Merchant application is pending approval.");
        ProblemDetail problem = handler.handleAccountPendingApproval(ex, request);

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
        assertEquals("Account Pending Approval", problem.getTitle());
        assertEquals(URI.create("https://swiftdrop.lk/errors/account-pending-approval"), problem.getType());
    }

    @Test
    @DisplayName("AccountSuspendedException returns HTTP 403 Forbidden ProblemDetail")
    void handleAccountSuspended() {
        AccountSuspendedException ex = new AccountSuspendedException("Courier account suspended due to violations.");
        ProblemDetail problem = handler.handleAccountSuspended(ex, request);

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
        assertEquals("Account Suspended", problem.getTitle());
        assertEquals(URI.create("https://swiftdrop.lk/errors/account-suspended"), problem.getType());
    }

    @Test
    @DisplayName("AccountRejectedException returns HTTP 403 Forbidden ProblemDetail")
    void handleAccountRejected() {
        AccountRejectedException ex = new AccountRejectedException("Courier application rejected.");
        ProblemDetail problem = handler.handleAccountRejected(ex, request);

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
        assertEquals("Account Application Rejected", problem.getTitle());
        assertEquals(URI.create("https://swiftdrop.lk/errors/account-rejected"), problem.getType());
    }

    @Test
    @DisplayName("BadCredentialsException with message returns HTTP 401 Unauthorized ProblemDetail")
    void handleBadCredentials_WithMessage() {
        BadCredentialsException ex = new BadCredentialsException("Invalid email or password.");
        ProblemDetail problem = handler.handleBadCredentials(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), problem.getStatus());
        assertEquals("Authentication Failed", problem.getTitle());
        assertEquals("Invalid email or password.", problem.getDetail());
        assertEquals(URI.create("https://swiftdrop.lk/errors/invalid-credentials"), problem.getType());
    }

    @Test
    @DisplayName("BadCredentialsException with null or blank message falls back to default detail")
    void handleBadCredentials_WithBlankOrNullMessage() {
        BadCredentialsException exBlank = new BadCredentialsException("   ");
        ProblemDetail problemBlank = handler.handleBadCredentials(exBlank, request);
        assertEquals("Invalid credentials provided.", problemBlank.getDetail());

        BadCredentialsException exNull = new BadCredentialsException(null);
        ProblemDetail problemNull = handler.handleBadCredentials(exNull, request);
        assertEquals("Invalid credentials provided.", problemNull.getDetail());
    }

    @Test
    @DisplayName("Generic AuthenticationException returns HTTP 401 Unauthorized ProblemDetail")
    void handleGenericAuthenticationException() {
        InsufficientAuthenticationException ex = new InsufficientAuthenticationException("Full authentication is required");
        ProblemDetail problem = handler.handleGenericAuthenticationException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), problem.getStatus());
        assertEquals("Authentication Required", problem.getTitle());
        assertEquals(URI.create("https://swiftdrop.lk/errors/unauthorized"), problem.getType());
    }

    @Test
    @DisplayName("AccessDeniedException returns HTTP 403 Forbidden ProblemDetail")
    void handleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden access");
        ProblemDetail problem = handler.handleAccessDenied(ex, request);

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
        assertEquals("Access Denied", problem.getTitle());
        assertEquals("Access denied. Insufficient permissions.", problem.getDetail());
        assertEquals(URI.create("https://swiftdrop.lk/errors/forbidden"), problem.getType());
    }

    @Test
    @DisplayName("DisabledException returns HTTP 403 Forbidden ProblemDetail")
    void handleDisabledException() {
        DisabledException ex = new DisabledException("User is disabled");
        ProblemDetail problem = handler.handleDisabledException(ex, request);

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
        assertEquals("Account Disabled", problem.getTitle());
        assertEquals("Account is disabled or pending approval.", problem.getDetail());
        assertEquals(URI.create("https://swiftdrop.lk/errors/account-disabled"), problem.getType());
    }

    @Test
    @DisplayName("InvalidRefreshTokenException returns HTTP 401 Unauthorized ProblemDetail")
    void handleInvalidRefreshToken() {
        InvalidRefreshTokenException ex = new InvalidRefreshTokenException("Refresh token expired or revoked");
        ProblemDetail problem = handler.handleInvalidRefreshToken(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), problem.getStatus());
        assertEquals("Invalid Refresh Token", problem.getTitle());
        assertEquals(URI.create("https://swiftdrop.lk/errors/invalid-refresh-token"), problem.getType());
    }

    @Test
    @DisplayName("ResourceNotFoundException returns HTTP 404 Not Found ProblemDetail")
    void handleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found with id 123");
        ProblemDetail problem = handler.handleResourceNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
        assertEquals("Resource Not Found", problem.getTitle());
        assertEquals(URI.create("https://swiftdrop.lk/errors/resource-not-found"), problem.getType());
    }

    @Test
    @DisplayName("NoResourceFoundException returns HTTP 404 Not Found ProblemDetail")
    void handleNoResourceFound() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/unknown/path");
        ProblemDetail problem = handler.handleNoResourceFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
        assertEquals("Not Found", problem.getTitle());
        assertEquals("The requested endpoint does not exist.", problem.getDetail());
        assertEquals(URI.create("https://swiftdrop.lk/errors/not-found"), problem.getType());
    }

    @Test
    @DisplayName("HttpMessageNotReadableException returns HTTP 400 Bad Request ProblemDetail")
    void handleHttpMessageNotReadable() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("JSON parse error: Unexpected character");
        ProblemDetail problem = handler.handleHttpMessageNotReadable(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Malformed JSON Request", problem.getTitle());
        assertEquals(URI.create("https://swiftdrop.lk/errors/malformed-json"), problem.getType());
    }

    @Test
    @DisplayName("MethodArgumentNotValidException returns HTTP 400 with field errors map")
    void handleValidationException() throws NoSuchMethodException {
        // Create mock method parameter
        Method dummyMethod = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", String.class);
        MethodParameter parameter = new MethodParameter(dummyMethod, 0);

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "targetObject");
        bindingResult.addError(new FieldError("targetObject", "email", "must not be blank"));
        bindingResult.addError(new FieldError("targetObject", "password", "size must be between 8 and 100"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);
        ProblemDetail problem = handler.handleValidationException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Validation Failed", problem.getTitle());
        assertEquals(URI.create("https://swiftdrop.lk/errors/validation-error"), problem.getType());

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problem.getProperties().get("errors");
        assertNotNull(errors);
        assertEquals("must not be blank", errors.get("email"));
        assertEquals("size must be between 8 and 100", errors.get("password"));
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException returns HTTP 405 Method Not Allowed")
    void handleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");
        ProblemDetail problem = handler.handleMethodNotSupported(ex, request);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED.value(), problem.getStatus());
        assertEquals("Method Not Allowed", problem.getTitle());
        assertTrue(problem.getDetail().contains("POST"));
        assertEquals(URI.create("https://swiftdrop.lk/errors/method-not-allowed"), problem.getType());
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException returns HTTP 415 Unsupported Media Type")
    void handleMediaTypeNotSupported() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException("text/plain");
        ProblemDetail problem = handler.handleMediaTypeNotSupported(ex, request);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), problem.getStatus());
        assertEquals("Unsupported Media Type", problem.getTitle());
        assertEquals(URI.create("https://swiftdrop.lk/errors/unsupported-media-type"), problem.getType());
    }

    @Test
    @DisplayName("MissingServletRequestParameterException returns HTTP 400 Bad Request")
    void handleMissingServletParameter() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("code", "String");
        ProblemDetail problem = handler.handleMissingServletParameter(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Missing Parameter", problem.getTitle());
        assertTrue(problem.getDetail().contains("'code' is missing"));
        assertEquals(URI.create("https://swiftdrop.lk/errors/missing-parameter"), problem.getType());
    }

    @Test
    @DisplayName("IllegalArgumentException returns HTTP 400 Bad Request ProblemDetail")
    void handleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid state provided");
        ProblemDetail problem = handler.handleIllegalArgument(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Bad Request", problem.getTitle());
        assertEquals("Invalid state provided", problem.getDetail());
        assertEquals(URI.create("https://swiftdrop.lk/errors/bad-request"), problem.getType());
    }

    @Test
    @DisplayName("IllegalStateException returns HTTP 409 Conflict ProblemDetail")
    void handleIllegalState() {
        IllegalStateException ex = new IllegalStateException("Parcel status already delivered");
        ProblemDetail problem = handler.handleIllegalState(ex, request);

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Invalid State Transition", problem.getTitle());
        assertEquals("Parcel status already delivered", problem.getDetail());
        assertEquals(URI.create("https://swiftdrop.lk/errors/invalid-state-transition"), problem.getType());
    }

    @Test
    @DisplayName("Generic Exception returns HTTP 500 Internal Server Error ProblemDetail")
    void handleGenericException() {
        RuntimeException ex = new RuntimeException("Unexpected database outage");
        ProblemDetail problem = handler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
        assertEquals("Internal Server Error", problem.getTitle());
        assertEquals("An unexpected server error occurred.", problem.getDetail());
        assertEquals(URI.create("https://swiftdrop.lk/errors/internal-server-error"), problem.getType());
    }

    // Helper dummy method for MethodParameter reflection
    @SuppressWarnings("unused")
    private void dummyTarget(String param) {}
}

