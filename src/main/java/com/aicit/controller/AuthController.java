package com.aicit.controller;

import com.aicit.dto.request.LoginRequest;
import com.aicit.dto.response.ApiResponse;
import com.aicit.dto.response.LoginResponse;
import com.aicit.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login endpoints for admin and institute users")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/admin/login")
    @Operation(summary = "MCA Admin login",
               description = "Returns a JWT for SUPER_ADMIN or MCA_ADMIN role.")
    public ResponseEntity<ApiResponse<LoginResponse>> adminLogin(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.adminLogin(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/institute/login")
    @Operation(summary = "Institute login",
               description = "Returns a JWT for INSTITUTE_ADMIN or INSTITUTE_STAFF role. " +
                              "Institute must be in APPROVED status.")
    public ResponseEntity<ApiResponse<LoginResponse>> instituteLogin(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.instituteLogin(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }
}
