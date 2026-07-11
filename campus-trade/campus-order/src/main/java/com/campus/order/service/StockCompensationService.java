package com.campus.order.service;

public interface StockCompensationService {
    void register(Long productId, String orderNo);
    void lockForOrderTransaction(String orderNo);
    void complete(String orderNo);
    void completeAfterCommit(String orderNo);
    void completeAfterCompletion(String orderNo);
    void retryOne(Long taskId);
}
