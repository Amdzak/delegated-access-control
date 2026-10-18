package com.example.backend.service;

import com.example.backend.dto.request.LoginRequest;
import com.example.backend.dto.response.LoginResponse;
import com.example.backend.dto.response.UserProfileResponse;
import com.example.backend.model.UserEntity;
import com.example.backend.repository.RolePrivilegeRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtTokenProvider;
import com.example.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RolePrivilegeRepository rolePrivilegeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public LoginResponse login(LoginRequest request) {
        log.debug("[AUTH_SERVICE_LOGIN]: DEBUG - Verifying credentials for username: '{}'", request.getUsername());

        UserEntity user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("[AUTH_SERVICE_LOGIN]: WARN - User '{}' not found in database", request.getUsername());
                    return new BadCredentialsException("Invalid username or password");
                });

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("[AUTH_SERVICE_LOGIN]: WARN - User account '{}' is disabled", user.getUsername());
            throw new BadCredentialsException("Account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("[AUTH_SERVICE_LOGIN]: WARN - Password mismatch for user: '{}'", user.getUsername());
            throw new BadCredentialsException("Invalid username or password");
        }


        UserPrincipal principal = UserPrincipal.create(user);
        String token = tokenProvider.generateToken(principal);
        String roleName = user.getRole() != null ? user.getRole().getRoleName() : "USER";

        log.info("[AUTH_SERVICE_LOGIN]: SUCCESS - Valid credentials, token issued for User={} ({}), Branch={}",
                user.getUsername(), roleName, user.getUnitCabang());

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .roleId(user.getRoleId())
                .roleName(roleName)
                .unitCabang(user.getUnitCabang())
                .build();
    }

    public UserProfileResponse getProfile(UserPrincipal principal) {
        log.debug("[AUTH_SERVICE_GET_PROFILE]: DEBUG - Loading privileges for user ID: '{}'", principal.getUserId());

        try {
            UserEntity user = userRepository.findByUserId(principal.getUserId())
                    .orElseThrow(() -> new BadCredentialsException("User not found: " + principal.getUserId()));

            List<Integer> permIds = rolePrivilegeRepository.findGrantedPermIdsByRoleId(user.getRoleId());

            log.info("[AUTH_SERVICE_GET_PROFILE]: SUCCESS - Loaded profile for user '{}' with {} privileges",
                    user.getUsername(), permIds.size());

            return UserProfileResponse.builder()
                    .userId(user.getUserId())
                    .username(user.getUsername())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .roleId(user.getRoleId())
                    .roleName(user.getRole() != null ? user.getRole().getRoleName() : "USER")
                    .unitCabang(user.getUnitCabang())
                    .grantedPermIds(permIds)
                    .build();
        } catch (Exception ex) {
            log.error("[AUTH_SERVICE_GET_PROFILE]: FAILED - Error loading profile for user ID '{}': {}",
                    principal.getUserId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
