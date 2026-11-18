package com.nyxora.swiftdrop.ai_assistant_service.exception;

import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public ResponseEntity<ProblemDetail> handleGeminiError(GeminiServiceException ex) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE,
                "AI Service Unavailable", "ai-service-unavailable",
                "The AI assistant is temporarily unavailable. Please try again later.", null);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(GeminiServiceException.class)
    public ResponseEntity<ProblemDetail> handleGeminiError(GeminiServiceException ex,
                                                           HttpServletRequest request) {
        logUnhandled(ex);
        return response(HttpStatus.SERVICE_UNAVAILABLE, "AI Service Unavailable",
                "ai-service-unavailable",
                "The AI assistant is temporarily unavailable. Please try again later.",
                request);
    }

    public ResponseEntity<ProblemDetail> handleValidationError(MethodArgumentNotValidException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid Request",
                "validation-error", "The supplied request is invalid.", null);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationError(MethodArgumentNotValidException ex,
                                                               HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        error -> PiiMaskingUtil.maskText(error.getDefaultMessage()),
                        (existing, replacement) -> existing));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid Request",
                "validation-error", "The supplied request is invalid.", request);
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidArgument(IllegalArgumentException ex,
                                                               HttpServletRequest request) {
        log.warn("Invalid AI assistant request");
        return response(HttpStatus.BAD_REQUEST, "Invalid Request", "invalid-request",
                "The supplied request is invalid.", request);
    }

    public ResponseEntity<ProblemDetail> handleInvalidArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(HttpStatus.BAD_REQUEST, "Invalid Request", "invalid-request",
                        "The supplied request is invalid.", null));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleMissingOrMalformedArgument(Exception ex,
                                                                          HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Invalid Request", "invalid-request",
                "The supplied request is invalid.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedJson(HttpMessageNotReadableException ex,
                                                             HttpServletRequest request) {
        log.warn("Malformed JSON request on {}", request.getRequestURI());
        return response(HttpStatus.BAD_REQUEST, "Malformed JSON", "malformed-json",
                "The request body contains malformed JSON.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(NoResourceFoundException ex,
                                                               HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "Not Found", "resource-not-found",
                "The requested resource was not found.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", "method-not-allowed",
                "The HTTP method is not supported for this endpoint.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                    HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
                "unsupported-media-type", "The request Content-Type is not supported.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedError(Exception ex,
                                                               HttpServletRequest request) {
        logUnhandled(ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "internal-server-error",
                "An unexpected error occurred. Please try again later.", request);
    }

    public ResponseEntity<ProblemDetail> handleUnexpectedError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                        "internal-server-error",
                        "An unexpected error occurred. Please try again later.", null));
    }

    private ResponseEntity<ProblemDetail> response(HttpStatus status, String title, String type,
                                                   String detail, HttpServletRequest request) {
        ProblemDetail problem = problem(status, title, type, detail, request);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ProblemDetail problem(HttpStatus status, String title, String type,
                                  String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://swiftdrop.lk/errors/" + type));
        if (request != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        return problem;
    }

    private void logUnhandled(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Unhandled AI assistant exception: {}", ex.getClass().getSimpleName());
    }
}