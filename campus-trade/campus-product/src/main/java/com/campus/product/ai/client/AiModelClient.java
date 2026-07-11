package com.campus.product.ai.client;

import java.util.List;

public interface AiModelClient {
    String completeJson(String systemPrompt, String userText, List<String> imageDataUrls);
}
