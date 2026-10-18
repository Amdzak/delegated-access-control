package com.example.backend.service;

import com.example.backend.dto.request.CreateDelegationRequest;
import com.example.backend.dto.response.DelegationResponse;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.exception.UnauthorizedDelegationException;
import com.example.backend.model.DelegationAuditEntity;
import com.example.backend.model.UserEntity;
import com.example.backend.repository.DelegationAuditRepository;
import com.example.backend.repository.RolePrivilegeRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DelegationService {

    private final UserRepository userRepository;
    private final RolePrivilegeRepository rolePrivilegeRepository;
    private final DelegationAuditRepository auditRepository;
    private final FabricGatewayService fabricGatewayService;

    @Transactional
    public DelegationResponse createDelegation(UserPrincipal delegator, CreateDelegationRequest request) {
        log.debug("[DELEGATION_SERVICE_CREATE]: DEBUG - Starting delegation process: Delegator='{}', Delegatee='{}', Module='{}', PermId={}",
                delegator.getUserId(), request.getDelegateeId(), request.getTargetModule(), request.getPermId());

        try {
            // 1. Validate Delegatee Existence
            UserEntity delegatee = userRepository.findByUserId(request.getDelegateeId())
                    .orElseThrow(() -> {
                        log.warn("[DELEGATION_SERVICE_CREATE]: WARN - Delegatee ID '{}' not found in database", request.getDelegateeId());
                        return new ResourceNotFoundException("Delegatee with ID " + request.getDelegateeId() + " not found");
                    });

            if (delegator.getUserId().equals(request.getDelegateeId())) {
                log.warn("[DELEGATION_SERVICE_CREATE]: WARN - Self-delegation attempted by user '{}'", delegator.getUserId());
                throw new UnauthorizedDelegationException("Delegator and Delegatee cannot be the same user");
            }

            // 2. Validate Delegator Original Privilege (Backend Policy Engine - Prevent Unauthorized Delegation)
            if (delegator.getRoleId() != 1) {
                boolean delegatorHasPerm = rolePrivilegeRepository.existsByRoleIdAndPermIdAndIsGrantedTrue(
                        delegator.getRoleId(), request.getPermId()
                );

                if (!delegatorHasPerm) {
                    log.warn("[DELEGATION_SERVICE_CREATE]: WARN - Privilege escalation attempt! Delegator '{}' ({}) attempted to delegate PermId={} without holding it",
                            delegator.getUserId(), delegator.getRoleName(), request.getPermId());
                    throw new UnauthorizedDelegationException(
                            "Authorization Violation: You do not possess granted authority for this module/permission."
                    );
                }
            }

            // 3. Validate Time Range
            if (!request.getValidUntil().isAfter(request.getValidFrom())) {
                log.warn("[DELEGATION_SERVICE_CREATE]: WARN - Invalid time range: validFrom='{}', validUntil='{}'",
                        request.getValidFrom(), request.getValidUntil());
                throw new IllegalArgumentException("Invalid time range: validUntil must be after validFrom");
            }

            // 4. Generate Unique Delegation ID (Format: DEL-YYYYMMDD-XXXXXXXX)
            String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            String delegationId = "DEL-" + datePrefix + "-" + randomSuffix;

            int maxDepth = request.getMaxDepth() != null ? request.getMaxDepth() : 2;
            String validFromStr = request.getValidFrom().atZone(java.time.ZoneId.systemDefault()).toInstant().toString();
            String validUntilStr = request.getValidUntil().atZone(java.time.ZoneId.systemDefault()).toInstant().toString();

            log.debug("[DELEGATION_SERVICE_CREATE]: DEBUG - Generated Delegation ID: '{}', MaxDepth={}", delegationId, maxDepth);

            // 5. Submit Transaction to Hyperledger Fabric Smart Contract (World State / Permission Registry)
            Map<String, Object> ledgerResult = fabricGatewayService.createDelegation(
                    delegationId,
                    delegator.getUserId(),
                    delegatee.getUserId(),
                    request.getTargetModule(),
                    request.getPermission(),
                    validFromStr,
                    validUntilStr,
                    maxDepth
            );

            String txId = (String) ledgerResult.getOrDefault("txId", "tx-" + UUID.randomUUID());

            // 6. Record Relational Audit Log to MSSQL Database
            DelegationAuditEntity auditLog = DelegationAuditEntity.builder()
                    .delegationId(delegationId)
                    .delegatorId(delegator.getUserId())
                    .delegateeId(delegatee.getUserId())
                    .targetModule(request.getTargetModule())
                    .permId(request.getPermId())
                    .actionType("GRANT")
                    .txId(txId)
                    .justification(request.getJustification())
                    .validFrom(request.getValidFrom())
                    .validUntil(request.getValidUntil())
                    .createdAt(LocalDateTime.now())
                    .build();

            auditRepository.save(auditLog);

            log.info("[DELEGATION_SERVICE_CREATE]: SUCCESS - Delegation created: ID='{}', Delegator='{}', Delegatee='{}', Module='{}', TxId='{}'",
                    delegationId, delegator.getUsername(), delegatee.getUsername(), request.getTargetModule(), txId);

            return DelegationResponse.builder()
                    .delegationId(delegationId)
                    .delegatorId(delegator.getUserId())
                    .delegatorName(delegator.getFullName())
                    .delegateeId(delegatee.getUserId())
                    .delegateeName(delegatee.getFullName())
                    .targetModule(request.getTargetModule())
                    .permId(request.getPermId())
                    .permission(request.getPermission())
                    .status("ACTIVE")
                    .txId(txId)
                    .justification(request.getJustification())
                    .validFrom(request.getValidFrom())
                    .validUntil(request.getValidUntil())
                    .createdAt(LocalDateTime.now())
                    .depth(0)
                    .maxDepth(maxDepth)
                    .build();
        } catch (Exception ex) {
            log.error("[DELEGATION_SERVICE_CREATE]: FAILED - Delegation creation failed: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @Transactional
    public void revokeDelegation(UserPrincipal principal, String delegationId, String reason) {
        log.debug("[DELEGATION_SERVICE_REVOKE]: DEBUG - Processing revocation for delegation ID '{}' by user '{}'",
                delegationId, principal.getUsername());

        try {
            List<DelegationAuditEntity> logs = auditRepository.findByDelegationIdOrderByCreatedAtDesc(delegationId);
            if (logs.isEmpty()) {
                log.warn("[DELEGATION_SERVICE_REVOKE]: WARN - Delegation ID '{}' not found in audit logs", delegationId);
                throw new ResourceNotFoundException("Delegation with ID " + delegationId + " not found");
            }

            DelegationAuditEntity latest = logs.get(0);

            // Only original delegator or admin may revoke
            if (!latest.getDelegatorId().equals(principal.getUserId()) && principal.getRoleId() != 1) {
                log.warn("[DELEGATION_SERVICE_REVOKE]: WARN - User '{}' unauthorized to revoke delegation '{}' owned by '{}'",
                        principal.getUserId(), delegationId, latest.getDelegatorId());
                throw new UnauthorizedDelegationException("Only the original delegator or an administrator may revoke this delegation.");
            }

            // 1. Submit Revocation to Fabric Smart Contract
            fabricGatewayService.revokeDelegation(delegationId);

            // 2. Record REVOKE Audit Log to MSSQL
            String revokeTxId = "tx-rev-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            DelegationAuditEntity revokeLog = DelegationAuditEntity.builder()
                    .delegationId(delegationId)
                    .delegatorId(latest.getDelegatorId())
                    .delegateeId(latest.getDelegateeId())
                    .targetModule(latest.getTargetModule())
                    .permId(latest.getPermId())
                    .actionType("REVOKE")
                    .txId(revokeTxId)
                    .justification("Authority revoked by " + principal.getFullName() + ": " + reason)
                    .validFrom(latest.getValidFrom())
                    .validUntil(latest.getValidUntil())
                    .createdAt(LocalDateTime.now())
                    .build();

            auditRepository.save(revokeLog);

            log.info("[DELEGATION_SERVICE_REVOKE]: SUCCESS - Delegation ID '{}' revoked by '{}', RevokeTxId='{}'",
                    delegationId, principal.getUsername(), revokeTxId);
        } catch (Exception ex) {
            log.error("[DELEGATION_SERVICE_REVOKE]: FAILED - Failed to revoke delegation ID '{}': {}", delegationId, ex.getMessage(), ex);
            throw ex;
        }
    }

    public List<DelegationAuditEntity> getAllAuditLogs() {
        log.debug("[DELEGATION_SERVICE_AUDIT]: DEBUG - Fetching all relational audit logs from MSSQL");
        List<DelegationAuditEntity> logs = auditRepository.findAllByOrderByCreatedAtDesc();
        log.info("[DELEGATION_SERVICE_AUDIT]: SUCCESS - Loaded {} delegation audit logs", logs.size());
        return logs;
    }

    public List<DelegationAuditEntity> getAuditLogsByDelegation(String delegationId) {
        log.debug("[DELEGATION_SERVICE_AUDIT_BY_ID]: DEBUG - Fetching audit logs for delegation ID: '{}'", delegationId);
        List<DelegationAuditEntity> logs = auditRepository.findByDelegationIdOrderByCreatedAtDesc(delegationId);
        log.info("[DELEGATION_SERVICE_AUDIT_BY_ID]: SUCCESS - Found {} audit logs for delegation ID '{}'", logs.size(), delegationId);
        return logs;
    }

    public List<Map<String, Object>> getBlockchainAuditTrail(String delegationId) {
        log.debug("[DELEGATION_SERVICE_BLOCKCHAIN_TRAIL]: DEBUG - Querying blockchain key mutation history for ID: '{}'", delegationId);
        try {
            List<Map<String, Object>> history = fabricGatewayService.getDelegationHistory(delegationId);
            log.info("[DELEGATION_SERVICE_BLOCKCHAIN_TRAIL]: SUCCESS - Loaded {} immutable blockchain history blocks for ID '{}'",
                    history.size(), delegationId);
            return history;
        } catch (Exception ex) {
            log.error("[DELEGATION_SERVICE_BLOCKCHAIN_TRAIL]: FAILED - Failed to read blockchain history for ID '{}': {}",
                    delegationId, ex.getMessage(), ex);
            throw ex;
        }
    }

    public List<DelegationResponse> getMyDelegations(UserPrincipal principal) {
        return getDelegationsByDelegateeId(principal.getUserId());
    }

    public List<DelegationResponse> getDelegationsByDelegateeId(String delegateeId) {
        log.debug("[DELEGATION_SERVICE_BY_DELEGATEE]: DEBUG - Fetching delegations for delegatee: '{}'", delegateeId);
        try {
            List<Map<String, Object>> rawList = fabricGatewayService.getDelegationsByDelegatee(delegateeId);
            List<DelegationResponse> responses = new ArrayList<>();

            for (Map<String, Object> d : rawList) {
                String delegationId = (String) d.get("delegationId");
                String delegatorId = (String) d.get("delegatorId");
                String delegatorName = userRepository.findByUserId(delegatorId)
                        .map(UserEntity::getFullName)
                        .orElse(delegatorId);

                String delegateeName = userRepository.findByUserId(delegateeId)
                        .map(UserEntity::getFullName)
                        .orElse(delegateeId);

                LocalDateTime validFrom = parseToLocalDateTime((String) d.get("validFrom"));
                LocalDateTime validUntil = parseToLocalDateTime((String) d.get("validUntil"));
                LocalDateTime createdAt = parseToLocalDateTime((String) d.get("createdAt"));

                Integer depth = d.get("depth") instanceof Number ? ((Number) d.get("depth")).intValue() : 0;
                Integer maxDepth = d.get("maxDepth") instanceof Number ? ((Number) d.get("maxDepth")).intValue() : 2;

                Integer permId = auditRepository.findByDelegationIdOrderByCreatedAtDesc(delegationId)
                        .stream()
                        .map(DelegationAuditEntity::getPermId)
                        .findFirst()
                        .orElse(null);

                DelegationResponse resp = DelegationResponse.builder()
                        .delegationId(delegationId)
                        .delegatorId(delegatorId)
                        .delegatorName(delegatorName)
                        .delegateeId(delegateeId)
                        .delegateeName(delegateeName)
                        .targetModule((String) d.get("resourceId"))
                        .permId(permId)
                        .permission((String) d.get("permission"))
                        .status((String) d.get("status"))
                        .validFrom(validFrom)
                        .validUntil(validUntil)
                        .createdAt(createdAt)
                        .depth(depth)
                        .maxDepth(maxDepth)
                        .build();

                responses.add(resp);
            }

            log.info("[DELEGATION_SERVICE_BY_DELEGATEE]: SUCCESS - Loaded {} delegations for delegatee '{}'",
                    responses.size(), delegateeId);
            return responses;
        } catch (Exception ex) {
            log.error("[DELEGATION_SERVICE_BY_DELEGATEE]: FAILED - Failed to fetch delegations for delegatee '{}': {}",
                    delegateeId, ex.getMessage(), ex);
            throw ex;
        }
    }

    private LocalDateTime parseToLocalDateTime(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        try {
            return java.time.Instant.parse(str).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(str);
            } catch (Exception ex) {
                return LocalDateTime.now();
            }
        }
    }
}

