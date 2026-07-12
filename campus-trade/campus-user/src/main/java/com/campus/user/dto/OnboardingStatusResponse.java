package com.campus.user.dto;

import lombok.Data;

import java.util.Map;

@Data
public class OnboardingStatusResponse {
    /** 是否展示新手教程区块 */
    private boolean visible;
    private boolean completed;
    private Map<String, Boolean> flags;
}
