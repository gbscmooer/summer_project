package com.campus.product.ai.service;

import com.campus.product.ai.dto.AiAdminConfigRequest;
import com.campus.product.ai.dto.AiAdminConfigResponse;
import com.campus.product.ai.dto.AiHealthStatusView;
import com.campus.product.ai.dto.AiRuntimeSettings;

public interface AiSettingsService {
    AiRuntimeSettings resolve();
    AiAdminConfigResponse getAdminView();
    AiHealthStatusView probeHealth();
    AiAdminConfigResponse saveAdminConfig(Long adminId, AiAdminConfigRequest request);
}
