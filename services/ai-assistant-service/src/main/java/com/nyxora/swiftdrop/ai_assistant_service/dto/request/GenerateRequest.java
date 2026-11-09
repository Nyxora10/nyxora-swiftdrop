package com.nyxora.swiftdrop.ai_assistant_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateRequest(

        @NotBlank(message = "Prompt is required")
        @Size(max = 2000,
              message = "Prompt must not exceed 2000 characters")
        String prompt

) {}