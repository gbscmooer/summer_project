package com.campus.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SpecialCertReviewRequest {
    @Size(max = 255, message = "审核备注最多255字")
    private String adminNote;
}
