package com.example.backend.controller;

import com.example.backend.dto.response.ApiResponse;
import com.example.backend.dto.response.ModuleResponse;
import com.example.backend.dto.response.RolePrivilegeResponse;
import com.example.backend.dto.response.UserSummaryResponse;
import com.example.backend.model.RoleEntity;
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
@Tag(name = "3. Master Roles & Users", description = "Query hospital master roles, staff users, modules, and privileges")
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

    @GetMapping("/modules")
    @Operation(summary = "Get All SIMRS Modules", description = "Retrieve list of all hospital modules from database")
    public ResponseEntity<ApiResponse<List<ModuleResponse>>> getAllModules() {
        log.debug("[ROLE_CONTROLLER_GET_MODULES]: DEBUG - Fetching all SIMRS modules");
        try {
            List<ModuleResponse> modules = roleService.getAllModules();
            log.info("[ROLE_CONTROLLER_GET_MODULES]: SUCCESS - Retrieved {} SIMRS modules", modules.size());
            return ResponseEntity.ok(ApiResponse.ok(modules));
        } catch (Exception ex) {
            log.error("[ROLE_CONTROLLER_GET_MODULES]: FAILED - Failed to retrieve SIMRS modules: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/users")
    @Operation(summary = "Get Hospital Staff Users", description = "Retrieve list of registered hospital staff users, optionally filtered by roleId (e.g. 101 for Doctors, 221 for IGD Nurses)")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getUsers(
            @RequestParam(required = false) Integer roleId) {
        log.debug("[USER_CONTROLLER_GET]: DEBUG - Fetching hospital staff users (roleId={})", roleId);
        try {
            List<UserSummaryResponse> users = roleService.getUsers(roleId);
            log.info("[USER_CONTROLLER_GET]: SUCCESS - Retrieved {} staff users (roleId filter={})", users.size(), roleId);
            return ResponseEntity.ok(ApiResponse.ok(users));
        } catch (Exception ex) {
            log.error("[USER_CONTROLLER_GET]: FAILED - Failed to retrieve staff users: {}", ex.getMessage(), ex);
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
