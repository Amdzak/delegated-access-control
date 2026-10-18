package com.example.backend.controller;

import com.example.backend.dto.request.LoginRequest;
import com.example.backend.dto.response.ApiResponse;
import com.example.backend.dto.response.LoginResponse;
import com.example.backend.dto.response.UserProfileResponse;
import com.example.backend.security.UserPrincipal;
import com.example.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "1. Authentication", description = "Authentication and user profile management endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "User Login", description = "Authenticate with username and password to obtain JWT token")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.debug("[LOGIN_CONTROLLER]: DEBUG - Processing login request for username: '{}'", request.getUsername());
        try {
            LoginResponse response = authService.login(request);
            log.info("[LOGIN_CONTROLLER]: SUCCESS - User '{}' ({}) authenticated, JWT issued",
                    response.getUsername(), response.getRoleName());
            return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
        } catch (Exception ex) {
            log.error("[LOGIN_CONTROLLER]: FAILED - Authentication failed for username '{}': {}",
                    request.getUsername(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get Current User Profile", description = "Retrieve profile details and granted static privileges for authenticated user")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        log.debug("[GET_CURRENT_USER_CONTROLLER]: DEBUG - Fetching profile for user ID: '{}'", principal.getUserId());
        try {
            UserProfileResponse response = authService.getProfile(principal);
            log.info("[GET_CURRENT_USER_CONTROLLER]: SUCCESS - Profile fetched for user '{}' ({})",
                    principal.getUsername(), principal.getRoleName());
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception ex) {
            log.error("[GET_CURRENT_USER_CONTROLLER]: FAILED - Failed to fetch profile for user '{}': {}",
                    principal.getUsername(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
