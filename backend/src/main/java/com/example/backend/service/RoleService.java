package com.example.backend.service;

import com.example.backend.dto.response.ModuleResponse;
import com.example.backend.dto.response.RolePrivilegeResponse;
import com.example.backend.dto.response.UserSummaryResponse;
import com.example.backend.model.RoleEntity;
import com.example.backend.model.RolePrivilegeEntity;
import com.example.backend.model.UserEntity;
import com.example.backend.repository.ModuleRepository;
import com.example.backend.repository.RolePrivilegeRepository;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePrivilegeRepository rolePrivilegeRepository;
    private final UserRepository userRepository;
    private final ModuleRepository moduleRepository;

    public List<RoleEntity> getAllRoles() {
        log.debug("[ROLE_SERVICE_GET_ALL]: DEBUG - Querying all roles from database");
        List<RoleEntity> roles = roleRepository.findAll();
        log.info("[ROLE_SERVICE_GET_ALL]: SUCCESS - Loaded {} hospital master roles", roles.size());
        return roles;
    }

    public List<ModuleResponse> getAllModules() {
        log.debug("[ROLE_SERVICE_GET_MODULES]: DEBUG - Querying all SIMRS modules from database");
        List<ModuleResponse> modules = moduleRepository.findAllByOrderByModuleIdAsc().stream()
                .map(m -> ModuleResponse.builder()
                        .moduleId(m.getModuleId())
                        .moduleName(m.getModuleName())
                        .build())
                .collect(Collectors.toList());
        log.info("[ROLE_SERVICE_GET_MODULES]: SUCCESS - Loaded {} SIMRS modules", modules.size());
        return modules;
    }

    public List<UserSummaryResponse> getUsers(Integer roleId) {
        log.debug("[ROLE_SERVICE_GET_USERS]: DEBUG - Querying staff users from database with roleId filter: {}", roleId);
        List<UserEntity> users;
        if (roleId != null) {
            users = userRepository.findByRoleIdAndIsActiveTrue(roleId);
        } else {
            users = userRepository.findAllByOrderByFullNameAsc();
        }
        log.info("[ROLE_SERVICE_GET_USERS]: SUCCESS - Loaded {} staff user accounts (roleId filter={})", users.size(), roleId);
        return users.stream().map(this::mapToUserSummary).collect(Collectors.toList());
    }

    public List<UserEntity> getAllUsers() {
        log.debug("[ROLE_SERVICE_GET_USERS]: DEBUG - Querying all staff users from database");
        List<UserEntity> users = userRepository.findAll();
        log.info("[ROLE_SERVICE_GET_USERS]: SUCCESS - Loaded {} staff user accounts", users.size());
        return users;
    }

    public List<RolePrivilegeResponse> getPrivilegesByRoleId(Integer roleId) {
        log.debug("[ROLE_SERVICE_GET_PRIVILEGES]: DEBUG - Querying active privileges for Role ID: {}", roleId);
        List<RolePrivilegeEntity> rps = rolePrivilegeRepository.findByRoleIdAndIsGrantedTrue(roleId);
        log.info("[ROLE_SERVICE_GET_PRIVILEGES]: SUCCESS - Found {} active privileges for Role ID: {}", rps.size(), roleId);
        return rps.stream().map(rp -> RolePrivilegeResponse.builder()
                .permId(rp.getPermId())
                .subPrivilegeName(rp.getPrivilege() != null ? rp.getPrivilege().getSubPrivilegeName() : "Perm #" + rp.getPermId())
                .moduleName(rp.getPrivilege() != null && rp.getPrivilege().getModule() != null ?
                        rp.getPrivilege().getModule().getModuleName() : "-")
                .build()
        ).collect(Collectors.toList());
    }

    private UserSummaryResponse mapToUserSummary(UserEntity user) {
        String roleName = user.getRole() != null ? user.getRole().getRoleName() : "USER";
        return UserSummaryResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .roleId(user.getRoleId())
                .roleName(roleName)
                .unitCabang(user.getUnitCabang())
                .isActive(user.getIsActive())
                .build();
    }
}
