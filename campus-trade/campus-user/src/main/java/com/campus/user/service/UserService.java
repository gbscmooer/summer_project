package com.campus.user.service;

import com.campus.user.dto.LoginRequest;
import com.campus.user.dto.LoginResponse;
import com.campus.user.dto.RegisterRequest;

public interface UserService {
    Long register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
}
