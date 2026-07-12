package com.campus.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApplyRatingRequest {

    @NotNull
    private Long sellerId;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;
}
