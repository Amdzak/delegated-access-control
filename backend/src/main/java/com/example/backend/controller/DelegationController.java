package com.example.backend.controller;

import com.example.backend.dto.request.CheckAccessRequest;
import com.example.backend.dto.request.CreateDelegationRequest;
import com.example.backend.dto.request.RevokeDelegationRequest;
import com.example.backend.dto.response.ApiResponse;
import com.example.backend.dto.response.DelegationResponse;
import com.example.backend.model.DelegationAuditEntity;
import com.example.backend.security.UserPrincipal;
import com.example.backend.service.DelegationService;
import com.example.backend.service.FabricGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/delegations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "2. Delegated Access Control", description = "Smart contract-based delegation lifecycle management on Hyperledger Fabric")
public class DelegationController {

    private final DelegationService delegationService;
    private final FabricGatewayService fabricGatewayService;

    @PostMapping
    @Operation(summary = "Grant Delegation", description = "Delegate temporary access authority to another user and record to Fabric Ledger & MSSQL Audit Log")
    public ResponseEntity<ApiResponse<DelegationResponse>> createDelegation(
            @AuthenticationPrincipal UserPrincipal principal,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Delegation creation payload",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CreateDelegationRequest.class),
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                    name = "Sample Emergency Delegation",
                                    value = """
                                            {
                                              "delegateeId": "USR-02101",
                                              "targetModule": "Logistik",
                                              "permId": 2899,
                                              "permission": "READ_WRITE",
                                              "validFrom": "2026-10-19 00:00:00",
                                              "validUntil": "2026-10-19 09:00:00",
                                              "justification": "Emergency coverage for inpatient care",
                                              "parentDelegationId": "",
                                              "maxDepth": 2
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody CreateDelegationRequest request) {
        log.debug("[DELEGATION_GRANT_CONTROLLER]: DEBUG - Processing delegation from '{}' to '{}' for module: '{}'",
                principal.getUserId(), request.getDelegateeId(), request.getTargetModule());
        try {
            DelegationResponse response = delegationService.createDelegation(principal, request);
            log.info("[DELEGATION_GRANT_CONTROLLER]: SUCCESS - Delegation ID '{}' created and committed to Fabric Ledger",
                    response.getDelegationId());
            return ResponseEntity.ok(ApiResponse.ok("Delegation authority successfully committed to Fabric Ledger", response));
        } catch (Exception ex) {
            log.error("[DELEGATION_GRANT_CONTROLLER]: FAILED - Failed to grant delegation: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @DeleteMapping("/{delegationId}")
    @Operation(summary = "Revoke Delegation", description = "Revoke an active delegation authority")
    public ResponseEntity<ApiResponse<Void>> revokeDelegation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String delegationId,
            @RequestBody(required = false) RevokeDelegationRequest request) {
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Manual revocation by delegator";
        log.debug("[DELEGATION_REVOKE_CONTROLLER]: DEBUG - Revocation requested for delegation '{}' by user '{}'",
                delegationId, principal.getUsername());
        try {
            delegationService.revokeDelegation(principal, delegationId, reason);
            log.info("[DELEGATION_REVOKE_CONTROLLER]: SUCCESS - Delegation ID '{}' successfully revoked by '{}'",
                    delegationId, principal.getUsername());
            return ResponseEntity.ok(ApiResponse.ok("Delegation authority successfully revoked", null));
        } catch (Exception ex) {
            log.error("[DELEGATION_REVOKE_CONTROLLER]: FAILED - Failed to revoke delegation ID '{}': {}",
                    delegationId, ex.getMessage(), ex);
            throw ex;
        }
    }

    @PostMapping("/check-access")
    @Operation(summary = "Check Access", description = "Verify delegation authority directly against Hyperledger Fabric Smart Contract")
    public ResponseEntity<ApiResponse<Map<String, String>>> checkAccess(@Valid @RequestBody CheckAccessRequest request) {
        log.debug("[CHECK_ACCESS_CONTROLLER]: DEBUG - Evaluating Smart Contract: DelId='{}', Delegatee='{}', Res='{}', Perm='{}'",
                request.getDelegationId(), request.getRequesterDelegateeId(), request.getResourceId(), request.getPermission());
        try {
            String result = fabricGatewayService.checkAccess(
                    request.getDelegationId(),
                    request.getRequesterDelegateeId(),
                    request.getResourceId(),
                    request.getPermission()
            );
            log.info("[CHECK_ACCESS_CONTROLLER]: SUCCESS - Smart contract validation for delegation '{}': Status={}",
                    request.getDelegationId(), result);
            return ResponseEntity.ok(ApiResponse.ok("Smart contract verification result", Map.of(
                    "status", result,
                    "isGranted", String.valueOf("GRANTED".equalsIgnoreCase(result))
            )));
        } catch (Exception ex) {
            log.error("[CHECK_ACCESS_CONTROLLER]: FAILED - Smart contract evaluation error: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Relational Audit Logs (MSSQL)", description = "Retrieve complete delegation transaction history from database")
    public ResponseEntity<ApiResponse<List<DelegationAuditEntity>>> getAllAuditLogs() {
        log.debug("[GET_AUDIT_LOGS_CONTROLLER]: DEBUG - Fetching relational delegation audit logs");
        try {
            List<DelegationAuditEntity> logs = delegationService.getAllAuditLogs();
            log.info("[GET_AUDIT_LOGS_CONTROLLER]: SUCCESS - Loaded {} delegation audit logs", logs.size());
            return ResponseEntity.ok(ApiResponse.ok(logs));
        } catch (Exception ex) {
            log.error("[GET_AUDIT_LOGS_CONTROLLER]: FAILED - Failed to load audit logs: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/my-delegations")
    @Operation(summary = "My Active Delegations (Blockchain)", description = "Retrieve all delegation authorities received by currently authenticated user directly from Hyperledger Fabric")
    public ResponseEntity<ApiResponse<List<DelegationResponse>>> getMyDelegations(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.debug("[MY_DELEGATIONS_CONTROLLER]: DEBUG - Fetching delegations for user '{}'", principal.getUsername());
        try {
            List<DelegationResponse> list = delegationService.getMyDelegations(principal);
            log.info("[MY_DELEGATIONS_CONTROLLER]: SUCCESS - Loaded {} delegations for user '{}'", list.size(), principal.getUsername());
            return ResponseEntity.ok(ApiResponse.ok("Active delegations retrieved from blockchain", list));
        } catch (Exception ex) {
            log.error("[MY_DELEGATIONS_CONTROLLER]: FAILED - Failed to load delegations for user '{}': {}",
                    principal.getUsername(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/by-delegatee/{delegateeId}")
    @Operation(summary = "Get Delegations by Delegatee ID", description = "Query all delegation authorities for a specific delegatee from Hyperledger Fabric")
    public ResponseEntity<ApiResponse<List<DelegationResponse>>> getDelegationsByDelegatee(
            @PathVariable String delegateeId) {
        log.debug("[DELEGATIONS_BY_DELEGATEE_CONTROLLER]: DEBUG - Fetching delegations for delegatee ID '{}'", delegateeId);
        try {
            List<DelegationResponse> list = delegationService.getDelegationsByDelegateeId(delegateeId);
            log.info("[DELEGATIONS_BY_DELEGATEE_CONTROLLER]: SUCCESS - Loaded {} delegations for delegatee ID '{}'", list.size(), delegateeId);
            return ResponseEntity.ok(ApiResponse.ok("Delegations retrieved from blockchain", list));
        } catch (Exception ex) {
            log.error("[DELEGATIONS_BY_DELEGATEE_CONTROLLER]: FAILED - Failed to load delegations for delegatee ID '{}': {}",
                    delegateeId, ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/{delegationId}/blockchain-history")
    @Operation(summary = "Blockchain Audit Trail (Fabric Ledger)", description = "Retrieve immutable key mutation history directly from Blockchain ledger")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBlockchainHistory(@PathVariable String delegationId) {
        log.debug("[GET_BLOCKCHAIN_HISTORY_CONTROLLER]: DEBUG - Fetching blockchain audit trail for ID: '{}'", delegationId);
        try {
            List<Map<String, Object>> history = delegationService.getBlockchainAuditTrail(delegationId);
            log.info("[GET_BLOCKCHAIN_HISTORY_CONTROLLER]: SUCCESS - Audit trail retrieved for ID '{}' ({} transaction blocks)",
                    delegationId, history.size());
            return ResponseEntity.ok(ApiResponse.ok(history));
        } catch (Exception ex) {
            log.error("[GET_BLOCKCHAIN_HISTORY_CONTROLLER]: FAILED - Failed to fetch blockchain audit trail for ID '{}': {}",
                    delegationId, ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/network-status")
    @Operation(summary = "Hyperledger Fabric Network Status", description = "Query realtime gRPC connectivity state of Fabric Peer")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNetworkStatus() {
        log.debug("[FABRIC_NETWORK_STATUS_CONTROLLER]: Querying realtime gRPC connectivity state");
        try {
            Map<String, Object> status = fabricGatewayService.getNetworkStatus();
            return ResponseEntity.ok(ApiResponse.ok("Fabric network status evaluated successfully", status));
        } catch (Exception ex) {
            log.error("[FABRIC_NETWORK_STATUS_CONTROLLER]: FAILED - Error inspecting network status: {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
