package com.nyxora.swiftdrop.notification.webhook.exception;

import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DeliveryLogNotFoundException.class)
    public ProblemDetail handleDeliveryLogNotFound(DeliveryLogNotFoundException ex,
                                                    HttpServletRequest request) {
        log.warn("Webhook delivery log not found");
        return problem(HttpStatus.NOT_FOUND, "Webhook Delivery Log Not Found",
                "delivery-log-not-found", "The requested webhook delivery log was not found.", request);
    }

    @ExceptionHandler(InvalidDeliveryStatusException.class)
    public ProblemDetail handleInvalidDeliveryStatus(InvalidDeliveryStatusException ex,
                                                      HttpServletRequest request) {
        log.warn("Webhook delivery log status conflict: {}", ex.getCurrentStatus());
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Invalid Delivery Status",
                "invalid-delivery-status",
                "The webhook delivery log is not in an eligible status for this action.", request);
        problem.setProperty("currentStatus", ex.getCurrentStatus());
        return problem;
    }

    @ExceptionHandler(ConcurrentRetryException.class)
    public ProblemDetail handleConcurrentRetry(ConcurrentRetryException ex,
                                               HttpServletRequest request) {
        log.warn("Concurrent webhook retry conflict");
        return problem(HttpStatus.CONFLICT, "Concurrent Retry Conflict",
                "concurrent-retry-conflict",
                "A retry for this webhook delivery is already being processed.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex,
                                           HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        error -> PiiMaskingUtil.maskText(error.getDefaultMessage()),
                        (existing, replacement) -> existing));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation Failed",
                "validation-error", "One or more fields failed validation.", request);
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ProblemDetail handleInvalidRequest(Exception ex, HttpServletRequest request) {
        log.warn("Invalid webhook request");
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", "bad-request",
                "The supplied request is invalid.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedJson(HttpMessageNotReadableException ex,
                                             HttpServletRequest request) {
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

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex,
                                            HttpServletRequest request) {
        log.error("Webhook retry state failure");
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "webhook-retry-publication-failed",
                "Webhook retry could not be published.", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected notification webhook error: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "internal-server-error", "An unexpected server error occurred.", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String type,
                                  String detail, HttpServletRequest request) {
        return WebhookProblemDetail.problem(status, title, type, detail, request.getRequestURI());
    }
}
