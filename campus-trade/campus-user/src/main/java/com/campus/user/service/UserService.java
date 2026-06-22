package com.campus.user.service;

import com.campus.user.dto.RegisterRequest;

public interface UserService {
    Long register(RegisterRequest request);
}
