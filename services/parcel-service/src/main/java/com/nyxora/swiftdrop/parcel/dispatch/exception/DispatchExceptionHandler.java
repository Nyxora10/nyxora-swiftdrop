package com.nyxora.swiftdrop.parcel.dispatch.exception;

import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import com.nyxora.swiftdrop.parcel.dispatch.controller.DispatchReportController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

/**
 * Isolated exception handler for Member 5's Dispatch controller.
 * Maps domain errors to standard RFC 7807 Problem Detail payloads without touching shared exception handlers.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = DispatchReportController.class)
public class DispatchExceptionHandler {

    @ExceptionHandler(CourierWorkloadExceededException.class)
    public ProblemDetail handleCourierWorkloadExceeded(CourierWorkloadExceededException ex,
                                                       HttpServletRequest request) {
        log.warn("[DispatchExceptionHandler] Courier workload exceeded: {}",
                PiiMaskingUtil.maskText(ex.getMessage()));
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Courier Workload Exceeded",
                "courier-workload-exceeded",
                "The courier has reached the maximum active parcel workload.", request);
        problem.setProperty("courierId", ex.getCourierId());
        problem.setProperty("currentActiveCount", ex.getCurrentActiveCount());
        return problem;
    }

    @ExceptionHandler(InvalidParcelStateException.class)
    public ProblemDetail handleInvalidParcelState(InvalidParcelStateException ex,
                                                  HttpServletRequest request) {
        log.warn("[DispatchExceptionHandler] Invalid parcel state: {}",
                PiiMaskingUtil.maskText(ex.getMessage()));
        return problem(HttpStatus.CONFLICT, "Invalid Parcel State",
                "invalid-parcel-state",
                "The parcel is not in a state that allows courier assignment.", request);
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
