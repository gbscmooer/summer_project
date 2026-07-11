package com.campus.product.ai.client;

import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.product.ai.dto.AiRuntimeSettings;
import com.campus.product.ai.service.AiSettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiCompatibleClientTest {

    @Test
    void rejectsImagesBeforeCallingTextOnlyModel() {
        AiSettingsService settingsService = mock(AiSettingsService.class);
        when(settingsService.resolve()).thenReturn(AiRuntimeSettings.builder()
                .baseUrl("https://api.openai.com/v1")
                .apiKey("test-key")
                .model("text-only")
                .timeoutSeconds(30)
                .supportsVision(false)
                .source("env")
                .build());
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(settingsService);

        BizException error = assertThrows(BizException.class,
                () -> client.completeJson("system", "user", List.of("data:image/png;base64,AQ==")));

        assertEquals(ResultCode.AI_VISION_NOT_SUPPORTED.getCode(), error.getCode());
    }
}
