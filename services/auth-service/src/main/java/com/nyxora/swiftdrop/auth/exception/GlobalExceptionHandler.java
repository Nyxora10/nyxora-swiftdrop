package com.nyxora.swiftdrop.auth.exception;

import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler converting domain, security, and validation exceptions
 * into RFC 7807 Problem Detail JSON representations.
 * Guarantees that client errors (bad JSON, expired tokens, wrong HTTP methods)
 * never leak 500 Internal Server Errors.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleEmailAlreadyExists(EmailAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Email conflict: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, PiiMaskingUtil.maskText(ex.getMessage()));
        problem.setTitle("Email Already Registered");
        problem.setType(URI.create("https://swiftdrop.lk/errors/email-already-exists"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(AccountPendingApprovalException.class)
    public ProblemDetail handleAccountPendingApproval(AccountPendingApprovalException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Account pending approval: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, PiiMaskingUtil.maskText(ex.getMessage()));
        problem.setTitle("Account Pending Approval");
        problem.setType(URI.create("https://swiftdrop.lk/errors/account-pending-approval"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ProblemDetail handleAccountSuspended(AccountSuspendedException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Suspended account access: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, PiiMaskingUtil.maskText(ex.getMessage()));
        problem.setTitle("Account Suspended");
        problem.setType(URI.create("https://swiftdrop.lk/errors/account-suspended"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(AccountRejectedException.class)
    public ProblemDetail handleAccountRejected(AccountRejectedException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Rejected account login attempt: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, PiiMaskingUtil.maskText(ex.getMessage()));
        problem.setTitle("Account Application Rejected");
        problem.setType(URI.create("https://swiftdrop.lk/errors/account-rejected"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Failed login attempt: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        String detail = PiiMaskingUtil.maskText(ex.getMessage());
        if (detail == null || detail.isBlank()) {
            detail = "Invalid credentials provided.";
        } else if ("Invalid email or password.".equals(detail)
                || "Invalid client_id or client_secret.".equals(detail)) {
            detail = "Invalid credentials provided.";
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                detail);
        problem.setTitle("Authentication Failed");
        problem.setType(URI.create("https://swiftdrop.lk/errors/invalid-credentials"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleGenericAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Authentication error on {}: {}",
                request.getRequestURI(), PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Authentication is required to access this resource.");
        problem.setTitle("Authentication Required");
        problem.setType(URI.create("https://swiftdrop.lk/errors/unauthorized"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Access denied on {}: {}",
                request.getRequestURI(), PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied. Insufficient permissions.");
        problem.setTitle("Access Denied");
        problem.setType(URI.create("https://swiftdrop.lk/errors/forbidden"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DisabledException.class)
    public ProblemDetail handleDisabledException(DisabledException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Disabled user login attempt: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Account is disabled or pending approval.");
        problem.setTitle("Account Disabled");
        problem.setType(URI.create("https://swiftdrop.lk/errors/account-disabled"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Invalid refresh token: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "The refresh token is invalid or expired.");
        problem.setTitle("Invalid Refresh Token");
        problem.setType(URI.create("https://swiftdrop.lk/errors/invalid-refresh-token"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Resource not found: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, PiiMaskingUtil.maskText(ex.getMessage()));
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://swiftdrop.lk/errors/resource-not-found"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Static endpoint not found: {}", request.getRequestURI());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The requested endpoint does not exist.");
        problem.setTitle("Not Found");
        problem.setType(URI.create("https://swiftdrop.lk/errors/not-found"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Malformed JSON request body on {}: {}",
                request.getRequestURI(), PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed JSON request body. Please verify that JSON quotes, commas, and fields are correctly structured."
        );
        problem.setTitle("Malformed JSON Request");
        problem.setType(URI.create("https://swiftdrop.lk/errors/malformed-json"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("[GlobalExceptionHandler] Validation failed on {}: {}",
                request.getRequestURI(), PiiMaskingUtil.maskText(errors.toString()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for one or more fields.");
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://swiftdrop.lk/errors/validation-error"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Method {} not supported on {}", ex.getMethod(), request.getRequestURI());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint."
        );
        problem.setTitle("Method Not Allowed");
        problem.setType(URI.create("https://swiftdrop.lk/errors/method-not-allowed"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Unsupported media type on {}: {}",
                request.getRequestURI(), PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type is not supported. Please use application/json."
        );
        problem.setTitle("Unsupported Media Type");
        problem.setType(URI.create("https://swiftdrop.lk/errors/unsupported-media-type"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingServletParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Missing parameter on {}: {}", request.getRequestURI(), ex.getParameterName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Required query parameter '" + ex.getParameterName() + "' is missing."
        );
        problem.setTitle("Missing Parameter");
        problem.setType(URI.create("https://swiftdrop.lk/errors/missing-parameter"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Illegal argument on {}: {}",
                request.getRequestURI(), PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, PiiMaskingUtil.maskText(ex.getMessage()));
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://swiftdrop.lk/errors/bad-request"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Conflict/Invalid state transition on {}: {}",
                request.getRequestURI(), PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, PiiMaskingUtil.maskText(ex.getMessage()));
        problem.setTitle("Invalid State Transition");
        problem.setType(URI.create("https://swiftdrop.lk/errors/invalid-state-transition"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("[GlobalExceptionHandler] Unhandled exception occurred on {}: {}",
                request.getRequestURI(), ex.getClass().getSimpleName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred.");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://swiftdrop.lk/errors/internal-server-error"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
