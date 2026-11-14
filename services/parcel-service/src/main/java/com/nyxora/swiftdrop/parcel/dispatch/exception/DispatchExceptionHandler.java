package com.nyxora.swiftdrop.parcel.dispatch.exception;

import com.nyxora.swiftdrop.parcel.dispatch.controller.DispatchReportController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Isolated exception handler for Member 5's Dispatch controller.
 * Maps domain errors to standard RFC 7807 Problem Detail payloads without touching shared exception handlers.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = DispatchReportController.class)
public class DispatchExceptionHandler {

    @ExceptionHandler(CourierWorkloadExceededException.class)
    public ProblemDetail handleCourierWorkloadExceeded(CourierWorkloadExceededException ex) {
        log.warn("[DispatchExceptionHandler] Courier workload exceeded: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Courier Workload Exceeded");
        problem.setProperty("courierId", ex.getCourierId());
        problem.setProperty("currentActiveCount", ex.getCurrentActiveCount());
        return problem;
    }

    @ExceptionHandler(InvalidParcelStateException.class)
    public ProblemDetail handleInvalidParcelState(InvalidParcelStateException ex) {
        log.warn("[DispatchExceptionHandler] Invalid parcel state: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Parcel State");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("[DispatchExceptionHandler] Bad request: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Request Parameters");
        return problem;
    }
}
