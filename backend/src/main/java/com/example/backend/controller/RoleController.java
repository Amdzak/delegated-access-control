package com.example.backend.controller;

import com.example.backend.dto.response.ApiResponse;
import com.example.backend.dto.response.RolePrivilegeResponse;
import com.example.backend.model.RoleEntity;
import com.example.backend.model.UserEntity;
import com.example.backend.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "3. Master Roles & Users", description = "Query hospital master roles, staff users, and privileges")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @Operation(summary = "Get All Roles", description = "Retrieve list of all hospital roles from database")
    public ResponseEntity<ApiResponse<List<RoleEntity>>> getAllRoles() {
        log.debug("[ROLE_CONTROLLER_GET_ALL]: DEBUG - Fetching all hospital master roles");
        try {
            List<RoleEntity> roles = roleService.getAllRoles();
            log.info("[ROLE_CONTROLLER_GET_ALL]: SUCCESS - Retrieved {} master roles", roles.size());
            return ResponseEntity.ok(ApiResponse.ok(roles));
        } catch (Exception ex) {
            log.error("[ROLE_CONTROLLER_GET_ALL]: FAILED - Failed to retrieve master roles: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/users")
    @Operation(summary = "Get All Users", description = "Retrieve list of all registered hospital staff users")
    public ResponseEntity<ApiResponse<List<UserEntity>>> getAllUsers() {
        log.debug("[USER_CONTROLLER_GET_ALL]: DEBUG - Fetching all hospital staff users");
        try {
            List<UserEntity> users = roleService.getAllUsers();
            log.info("[USER_CONTROLLER_GET_ALL]: SUCCESS - Retrieved {} staff users", users.size());
            return ResponseEntity.ok(ApiResponse.ok(users));
        } catch (Exception ex) {
            log.error("[USER_CONTROLLER_GET_ALL]: FAILED - Failed to retrieve staff users: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/{roleId}/privileges")
    @Operation(summary = "Get Role Privileges", description = "Retrieve list of granted privileges for a specific role")
    public ResponseEntity<ApiResponse<List<RolePrivilegeResponse>>> getPrivilegesByRole(@PathVariable Integer roleId) {
        log.debug("[ROLE_PRIVILEGES_CONTROLLER]: DEBUG - Fetching privileges for Role ID: {}", roleId);
        try {
            List<RolePrivilegeResponse> privileges = roleService.getPrivilegesByRoleId(roleId);
            log.info("[ROLE_PRIVILEGES_CONTROLLER]: SUCCESS - Retrieved {} privileges for Role ID: {}",
                    privileges.size(), roleId);
            return ResponseEntity.ok(ApiResponse.ok(privileges));
        } catch (Exception ex) {
            log.error("[ROLE_PRIVILEGES_CONTROLLER]: FAILED - Failed to retrieve privileges for Role ID {}: {}",
                    roleId, ex.getMessage(), ex);
            throw ex;
        }
    }
}
