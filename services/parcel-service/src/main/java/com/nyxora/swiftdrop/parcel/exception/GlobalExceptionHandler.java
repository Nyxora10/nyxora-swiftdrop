package com.nyxora.swiftdrop.parcel.exception;

import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.nyxora.swiftdrop.parcel.statemachine.InvalidStateTransitionException;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for the Parcel Service.
 * Returns RFC 7807 Problem Detail responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Bad request: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", "bad-request",
                "The supplied request is invalid.", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        return problem(HttpStatus.NOT_FOUND, "Resource Not Found", "resource-not-found",
                "The requested resource was not found.", request);
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ProblemDetail handleResourceConflict(ResourceConflictException ex, HttpServletRequest request) {
        log.warn("Resource conflict: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        return problem(HttpStatus.CONFLICT, "Resource Conflict", "resource-conflict",
                "The request conflicts with existing resource state.", request);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ProblemDetail handleInvalidStateTransition(InvalidStateTransitionException ex, HttpServletRequest request) {
        log.warn("Invalid parcel state transition: from={} to={}, reason={}",
                ex.getFromStatus(), ex.getToStatus(), PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Invalid State Transition",
                "invalid-state-transition", "The requested parcel state transition is not allowed.", request);
        if (ex.getFromStatus() != null) {
            problem.setProperty("fromStatus", ex.getFromStatus().name());
        }
        if (ex.getToStatus() != null) {
            problem.setProperty("toStatus", ex.getToStatus().name());
        }
        return problem;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingRequestHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Missing Request Header",
                "missing-request-header", "A required request header is missing.", request);
        problem.setProperty("header", ex.getHeaderName());
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch for parameter or header '{}': {}",
                ex.getName(), PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Type Mismatch",
                "invalid-request-value", "A request parameter or header has an invalid format.", request);
        problem.setProperty("parameter", ex.getName());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "forbidden",
                "You do not have permission to perform this action.", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.error("Internal state error: {}", PiiMaskingUtil.maskText(ex.getMessage()));
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "internal-server-error", "An unexpected server error occurred.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage,
                        (existing, replacement) -> existing));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation Failed",
                "validation-error", "One or more fields failed validation.", request);
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed JSON request on {}", request.getRequestURI());
        return problem(HttpStatus.BAD_REQUEST, "Malformed JSON", "malformed-json",
                "The request body contains malformed JSON.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed",
                "method-not-allowed", "The HTTP method is not supported for this endpoint.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                    HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
                "unsupported-media-type", "The request Content-Type is not supported.", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "internal-server-error", "An unexpected error occurred.", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String type,
                                  String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://swiftdrop.lk/errors/" + type));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
