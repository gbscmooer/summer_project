package com.campus.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MarkOnboardingStepRequest {
    @NotBlank
    private String step;
}
