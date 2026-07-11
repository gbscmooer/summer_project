package com.campus.product.controller;

import com.campus.common.result.Result;
import com.campus.product.ai.dto.AiListingDraftRequest;
import com.campus.product.ai.dto.AiListingDraftResponse;
import com.campus.product.ai.dto.AiSearchRequest;
import com.campus.product.ai.dto.AiSearchResponse;
import com.campus.product.ai.service.AiAssistantService;
import com.campus.product.ai.service.AiUsageGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAssistantService aiAssistantService;
    private final AiUsageGuard aiUsageGuard;

    /** 需登录：网关注入 X-User-Id，避免匿名消耗 API Key */
    @PostMapping("/search")
    public Result<AiSearchResponse> search(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody AiSearchRequest request) {
        return aiUsageGuard.execute(userId,
                () -> Result.success(aiAssistantService.search(request)));
    }

    @PostMapping("/listing-draft")
    public Result<AiListingDraftResponse> listingDraft(
            @RequestHeader("X-User-Id") Long sellerId,
            @Valid @RequestBody AiListingDraftRequest request) {
        return aiUsageGuard.execute(sellerId,
                () -> Result.success(aiAssistantService.createListingDraft(sellerId, request)));
    }
}
