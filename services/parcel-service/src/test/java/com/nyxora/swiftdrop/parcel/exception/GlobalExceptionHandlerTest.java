package com.nyxora.swiftdrop.parcel.exception;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.statemachine.InvalidStateTransitionException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validationFailureReturns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("One or more fields failed validation."))
                .andExpect(jsonPath("$.instance").value("/test/validation"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void missingResourceReturns404WithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://swiftdrop.lk/errors/resource-not-found"))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("The requested resource was not found."));
    }

    @Test
    void accessDeniedReturnsSafe403() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://swiftdrop.lk/errors/forbidden"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));
    }

    @Test
    void invalidStateTransitionReturns409() throws Exception {
        mockMvc.perform(get("/test/state-transition"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://swiftdrop.lk/errors/invalid-state-transition"))
                .andExpect(jsonPath("$.title").value("Invalid State Transition"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("The requested parcel state transition is not allowed."));
    }

    @Test
    void resourceConflictReturns409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Resource Conflict"));
    }

    @Test
    void unexpectedExceptionReturnsSafe500() throws Exception {
        mockMvc.perform(get("/test/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @RestController
    static class TestController {
        @PostMapping("/test/validation")
        public void validation(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/not-found")
        public void notFound() {
            throw new ResourceNotFoundException(
                    "SQL select password=secret owner_email=user@swiftdrop.lk");
        }

        @GetMapping("/test/forbidden")
        public void forbidden() {
            throw new AccessDeniedException("Bearer raw-token password=secret");
        }

        @GetMapping("/test/state-transition")
        public void stateTransition() {
            throw new InvalidStateTransitionException(
                    ParcelStatus.CREATED, ParcelStatus.DELIVERED,
                    "password=secret deliveryAddress=123 Main Road");
        }

        @GetMapping("/test/conflict")
        public void conflict() {
            throw new ResourceConflictException(
                    "Duplicate key SQL error email=user@swiftdrop.lk");
        }

        @GetMapping("/test/internal")
        public void internal() {
            throw new RuntimeException(
                    "SQL exception password=secret token=raw-jwt");
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
