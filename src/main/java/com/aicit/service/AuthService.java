package com.aicit.service;

import com.aicit.dto.request.LoginRequest;
import com.aicit.dto.response.LoginResponse;

public interface AuthService {
    LoginResponse adminLogin(LoginRequest request);
    LoginResponse instituteLogin(LoginRequest request);
}
