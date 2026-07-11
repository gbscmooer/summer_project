package com.campus.product.ai.service;

import com.campus.product.ai.dto.AiListingDraftRequest;
import com.campus.product.ai.dto.AiListingDraftResponse;
import com.campus.product.ai.dto.AiSearchRequest;
import com.campus.product.ai.dto.AiSearchResponse;

public interface AiAssistantService {
    AiSearchResponse search(AiSearchRequest request);
    AiListingDraftResponse createListingDraft(Long sellerId, AiListingDraftRequest request);
}
