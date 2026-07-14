package com.campus.user.service;

import com.campus.user.dto.SpecialCertApplicationRequest;
import com.campus.user.dto.SpecialCertApplicationVO;

import java.util.List;

public interface SpecialCertApplicationService {

    void apply(Long userId, SpecialCertApplicationRequest request);

    SpecialCertApplicationVO getMyApplication(Long userId);

    List<SpecialCertApplicationVO> listPending();

    void approve(Long adminId, Long applicationId, String adminNote);

    void reject(Long adminId, Long applicationId, String adminNote);
}
