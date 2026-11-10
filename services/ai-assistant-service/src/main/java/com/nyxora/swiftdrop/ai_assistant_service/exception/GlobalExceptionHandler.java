package com.nyxora.swiftdrop.ai_assistant_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GeminiServiceException.class)
    public ResponseEntity<ProblemDetail> handleGeminiError(
            GeminiServiceException ex) {

        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.SERVICE_UNAVAILABLE
        );

        problem.setTitle("AI Service Unavailable");
        problem.setDetail(
                "The AI assistant is temporarily unavailable. "
                + "Please try again later."
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationError(
            MethodArgumentNotValidException ex) {

        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST
        );

        problem.setTitle("Invalid Request");
        problem.setDetail(
                "Please provide a valid prompt "
                + "containing no more than 2000 characters."
        );

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidArgument(
            IllegalArgumentException ex) {

        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST
        );

        problem.setTitle("Invalid Request");
        problem.setDetail("The supplied request is invalid.");

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedError(
            Exception ex) {

        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.INTERNAL_SERVER_ERROR
        );

        problem.setTitle("Internal Server Error");
        problem.setDetail(
                "An unexpected error occurred. Please try again later."
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem);
    }
}