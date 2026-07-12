package com.campus.user.service;

import com.campus.user.dto.MerchantApplicationRequest;
import com.campus.user.dto.MerchantApplicationVO;

import java.util.List;

public interface MerchantApplicationService {

    void apply(Long userId, MerchantApplicationRequest request);

    MerchantApplicationVO getMyApplication(Long userId);

    List<MerchantApplicationVO> listPending();

    void approve(Long adminId, Long applicationId, String adminNote);

    void reject(Long adminId, Long applicationId, String adminNote);
}
