package com.nyxora.swiftdrop.notification.webhook.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.net.URI;

public final class WebhookProblemDetail {

    private WebhookProblemDetail() {
    }

    public static ProblemDetail problem(HttpStatus status, String title, String type,
                                        String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://swiftdrop.lk/errors/" + type));
        problem.setInstance(URI.create(instance));
        return problem;
    }

    public static ResponseEntity<ProblemDetail> response(HttpStatus status, String title, String type,
                                                         String detail, String instance) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(status, title, type, detail, instance));
    }
}
