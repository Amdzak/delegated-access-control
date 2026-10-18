package com.example.backend.security;

import com.example.backend.repository.RolePrivilegeRepository;
import com.example.backend.service.FabricGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final RolePrivilegeRepository rolePrivilegeRepository;
    private final FabricGatewayService fabricGatewayService;

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomain, Object permissionId) {
        if (auth == null || targetDomain == null || permissionId == null) {
            log.debug("[PERMISSION_EVALUATOR]: DEBUG - Evaluation parameters null (auth, domain, or permissionId)");
            return false;
        }

        if (!(auth.getPrincipal() instanceof UserPrincipal user)) {
            log.debug("[PERMISSION_EVALUATOR]: DEBUG - Principal is not an instance of UserPrincipal");
            return false;
        }

        String userId = user.getUserId();
        int permId;
        try {
            permId = Integer.parseInt(permissionId.toString());
        } catch (NumberFormatException e) {
            log.error("[PERMISSION_EVALUATOR]: FAILED - Invalid Permission ID format '{}'", permissionId, e);
            return false;
        }
        String moduleName = targetDomain.toString();

        log.debug("[PERMISSION_EVALUATOR]: DEBUG - Starting two-tier authorization check: User='{}' ({}), Module='{}', PermId={}",
                userId, user.getRoleName(), moduleName, permId);

        // TIER 1: Static RBAC Evaluation (SQL Server dac_morbis)
        boolean hasStaticRbac = rolePrivilegeRepository.existsByRoleIdAndPermIdAndIsGrantedTrue(
                user.getRoleId(), permId
        );
        if (hasStaticRbac) {
            log.info("[PERMISSION_EVALUATOR]: SUCCESS - Access GRANTED via Tier 1 (Static RBAC): User='{}' ({}), Module='{}', PermId={}",
                    userId, user.getRoleName(), moduleName, permId);
            return true;
        }

        // TIER 2: Delegated Access Control Evaluation (Hyperledger Fabric Smart Contract)
        boolean hasDelegatedAccess = fabricGatewayService.validateDelegation(
                userId, moduleName, permId
        );

        if (hasDelegatedAccess) {
            log.info("[PERMISSION_EVALUATOR]: SUCCESS - Access GRANTED via Tier 2 (Fabric Smart Contract DAC): User='{}' ({}), Module='{}', PermId={}",
                    userId, user.getRoleName(), moduleName, permId);
            return true;
        }

        log.warn("[PERMISSION_EVALUATOR]: WARN - Access DENIED: User='{}' ({}) lacks both static privilege and active delegation for Module='{}', PermId={}",
                userId, user.getRoleName(), moduleName, permId);
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object permission) {
        return hasPermission(auth, targetType, permission);
    }
}
