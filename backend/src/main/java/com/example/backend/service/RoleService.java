package com.example.backend.service;

import com.example.backend.dto.response.RolePrivilegeResponse;
import com.example.backend.model.RoleEntity;
import com.example.backend.model.RolePrivilegeEntity;
import com.example.backend.model.UserEntity;
import com.example.backend.repository.RolePrivilegeRepository;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePrivilegeRepository rolePrivilegeRepository;
    private final UserRepository userRepository;

    public List<RoleEntity> getAllRoles() {
        log.debug("[ROLE_SERVICE_GET_ALL]: DEBUG - Querying all roles from database");
        List<RoleEntity> roles = roleRepository.findAll();
        log.info("[ROLE_SERVICE_GET_ALL]: SUCCESS - Loaded {} hospital master roles", roles.size());
        return roles;
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
}
