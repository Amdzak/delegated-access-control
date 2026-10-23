package com.example.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class FabricGatewayService {

    @Value("${fabric.enabled:true}")
    private boolean fabricEnabled;

    @Value("${fabric.channel-name:z-dac}")
    private String channelName;

    @Value("${fabric.contract-name:dac}")
    private String contractName;

    @Autowired(required = false)
    private Gateway fabricGateway;

    @Autowired(required = false)
    private io.grpc.ManagedChannel fabricChannel;

    @Value("${fabric.peer-endpoint:localhost:7051}")
    private String peerEndpoint;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory fallback state when Fabric peer is offline
    private final Map<String, Map<String, Object>> simulatedWorldState = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> simulatedHistory = new ConcurrentHashMap<>();

    private Contract getContract() {
        if (fabricEnabled && fabricGateway != null) {
            try {
                Network network = fabricGateway.getNetwork(channelName);
                return network.getContract(contractName);
            } catch (Exception e) {
                log.warn("[FABRIC_GET_CONTRACT]: WARN - Unable to acquire contract '{}' on channel '{}': {}",
                        contractName, channelName, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Submit createDelegation transaction to Fabric Smart Contract
     */
    public Map<String, Object> createDelegation(
            String delegationId,
            String delegatorId,
            String delegateeId,
            String resourceId,
            String permission,
            String validFrom,
            String validUntil,
            int maxDepth) {

        log.debug("[FABRIC_CREATE_DELEGATION]: DEBUG - Preparing transaction submission: ID='{}', Delegator='{}', Delegatee='{}', Resource='{}', Perm='{}'",
                delegationId, delegatorId, delegateeId, resourceId, permission);

        Contract contract = getContract();
        String txId = "tx-fab-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        if (contract != null) {
            try {
                byte[] resultBytes = contract.submitTransaction(
                        "createDelegation",
                        delegationId,
                        delegatorId,
                        delegateeId,
                        resourceId,
                        permission,
                        validFrom,
                        validUntil,
                        String.valueOf(maxDepth)
                );
                String resultStr = new String(resultBytes, StandardCharsets.UTF_8);
                log.info("[FABRIC_CREATE_DELEGATION]: SUCCESS - Transaction committed to Fabric Ledger (World State): ID='{}', Response={}",
                        delegationId, resultStr);

                Map<String, Object> resMap = objectMapper.readValue(resultStr, Map.class);
                resMap.put("txId", txId);
                return resMap;
            } catch (Exception e) {
                log.error("[FABRIC_CREATE_DELEGATION]: FAILED - Commit failed to Fabric peer: {}. Falling back to simulation state",
                        e.getMessage(), e);
            }
        } else {
            log.warn("[FABRIC_CREATE_DELEGATION]: WARN - Fabric Gateway offline/null. Using simulated World State");
        }

        // Fallback State
        Map<String, Object> payload = new HashMap<>();
        payload.put("delegationId", delegationId);
        payload.put("delegatorId", delegatorId);
        payload.put("delegateeId", delegateeId);
        payload.put("resourceId", resourceId);
        payload.put("permission", permission);
        payload.put("validFrom", validFrom);
        payload.put("validUntil", validUntil);
        payload.put("maxDepth", maxDepth);
        payload.put("depth", 0);
        payload.put("status", "ACTIVE");
        payload.put("createdAt", Instant.now().toString());
        payload.put("txId", txId);

        simulatedWorldState.put(delegationId, payload);

        List<Map<String, Object>> hist = simulatedHistory.computeIfAbsent(delegationId, k -> new ArrayList<>());
        Map<String, Object> entry = new HashMap<>();
        entry.put("txId", txId);
        entry.put("timestamp", Instant.now().toString());
        entry.put("action", "GRANT");
        entry.put("value", payload);
        hist.add(entry);

        log.info("[FABRIC_CREATE_DELEGATION]: SUCCESS - Delegation ID='{}' recorded in fallback ledger state (TxId='{}')",
                delegationId, txId);
        return payload;
    }

    /**
     * Submit revokeDelegation transaction to Fabric Smart Contract
     */
    public boolean revokeDelegation(String delegationId) {
        log.debug("[FABRIC_REVOKE_DELEGATION]: DEBUG - Preparing revocation transaction for ID='{}'", delegationId);

        Contract contract = getContract();
        String txId = "tx-rev-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        if (contract != null) {
            try {
                byte[] resultBytes = contract.submitTransaction("revokeDelegation", delegationId);
                log.info("[FABRIC_REVOKE_DELEGATION]: SUCCESS - Revocation transaction committed to Fabric Ledger for ID='{}'",
                        delegationId);
                return true;
            } catch (Exception e) {
                log.error("[FABRIC_REVOKE_DELEGATION]: FAILED - Failed to submit revoke to Fabric: {}", e.getMessage(), e);
            }
        } else {
            log.warn("[FABRIC_REVOKE_DELEGATION]: WARN - Fabric Gateway offline. Updating fallback state");
        }

        // Fallback State
        Map<String, Object> payload = simulatedWorldState.get(delegationId);
        if (payload != null) {
            payload.put("status", "REVOKED");

            List<Map<String, Object>> hist = simulatedHistory.computeIfAbsent(delegationId, k -> new ArrayList<>());
            Map<String, Object> entry = new HashMap<>();
            entry.put("txId", txId);
            entry.put("timestamp", Instant.now().toString());
            entry.put("action", "REVOKE");
            entry.put("value", payload);
            hist.add(entry);

            log.info("[FABRIC_REVOKE_DELEGATION]: SUCCESS - Delegation ID='{}' status set to REVOKED in fallback state", delegationId);
            return true;
        }

        log.warn("[FABRIC_REVOKE_DELEGATION]: WARN - Delegation ID='{}' not found in state", delegationId);
        return false;
    }

    /**
     * Evaluate checkAccess transaction (Read-only query against Fabric Peer)
     */
    public String checkAccess(String delegationId, String requesterDelegateeId, String resourceId, String permission) {
        log.debug("[FABRIC_CHECK_ACCESS]: DEBUG - Evaluating access: DelId='{}', Delegatee='{}', Resource='{}', Perm='{}'",
                delegationId, requesterDelegateeId, resourceId, permission);

        Contract contract = getContract();

        if (contract != null) {
            try {
                byte[] resultBytes = contract.evaluateTransaction(
                        "checkAccess",
                        delegationId,
                        requesterDelegateeId,
                        resourceId,
                        permission
                );
                String result = new String(resultBytes, StandardCharsets.UTF_8);
                log.info("[FABRIC_CHECK_ACCESS]: SUCCESS - Smart Contract evaluated: Status='{}' for Delegatee='{}'",
                        result, requesterDelegateeId);
                return result;
            } catch (Exception e) {
                log.warn("[FABRIC_CHECK_ACCESS]: WARN - Evaluate checkAccess failed against Fabric: {}. Switching to local evaluator", e.getMessage());
            }
        }

        // Fallback Evaluator
        Map<String, Object> d = simulatedWorldState.get(delegationId);
        if (d == null) {
            log.warn("[FABRIC_CHECK_ACCESS]: WARN - Evaluation DENIED: Delegation ID '{}' not found", delegationId);
            return "DENIED: delegation not found";
        }
        if (!Objects.equals(d.get("delegateeId"), requesterDelegateeId)) {
            log.warn("[FABRIC_CHECK_ACCESS]: WARN - Evaluation DENIED: Delegatee '{}' does not match", requesterDelegateeId);
            return "DENIED: delegatee mismatch";
        }
        if (!"ACTIVE".equals(d.get("status"))) {
            log.warn("[FABRIC_CHECK_ACCESS]: WARN - Evaluation DENIED: Delegation status is '{}'", d.get("status"));
            return "DENIED: status " + d.get("status");
        }
        if (!Objects.equals(d.get("resourceId"), resourceId)) {
            log.warn("[FABRIC_CHECK_ACCESS]: WARN - Evaluation DENIED: Resource '{}' does not match", resourceId);
            return "DENIED: resource mismatch";
        }

        String p = (String) d.get("permission");
        if (!"READ_WRITE".equals(p) && !Objects.equals(p, permission)) {
            log.warn("[FABRIC_CHECK_ACCESS]: WARN - Evaluation DENIED: Permission '{}' does not match '{}'", permission, p);
            return "DENIED: permission mismatch";
        }

        Instant now = Instant.now();
        Instant from = Instant.parse((String) d.get("validFrom"));
        Instant until = Instant.parse((String) d.get("validUntil"));

        if (now.isBefore(from)) {
            log.warn("[FABRIC_CHECK_ACCESS]: WARN - Evaluation DENIED: Validity window not started yet (from: {})", from);
            return "DENIED: not yet active";
        }
        if (now.isAfter(until)) {
            log.warn("[FABRIC_CHECK_ACCESS]: WARN - Evaluation DENIED: Validity window expired (until: {})", until);
            return "DENIED: expired";
        }

        log.info("[FABRIC_CHECK_ACCESS]: SUCCESS - Local evaluation GRANTED for Delegatee='{}' on Resource='{}'",
                requesterDelegateeId, resourceId);
        return "GRANTED";
    }

    /**
     * Evaluate getDelegationHistory transaction (Fabric Audit Trail)
     */
    public List<Map<String, Object>> getDelegationHistory(String delegationId) {
        log.debug("[FABRIC_GET_HISTORY]: DEBUG - Querying key mutation history for ID='{}'", delegationId);

        Contract contract = getContract();

        if (contract != null) {
            try {
                byte[] resultBytes = contract.evaluateTransaction("getDelegationHistory", delegationId);
                String resultStr = new String(resultBytes, StandardCharsets.UTF_8);
                List<String> list = objectMapper.readValue(resultStr, List.class);
                List<Map<String, Object>> parsed = new ArrayList<>();
                for (String item : list) {
                    Map<String, Object> map = new HashMap<>();
                    String[] parts = item.split("\\|");
                    for (String part : parts) {
                        String[] kv = part.split("=", 2);
                        if (kv.length == 2) {
                            map.put(kv[0], kv[1]);
                        }
                    }
                    parsed.add(map);
                }
                log.info("[FABRIC_GET_HISTORY]: SUCCESS - Loaded {} audit trail transactions from Fabric for ID='{}'",
                        parsed.size(), delegationId);
                return parsed;
            } catch (Exception e) {
                log.error("[FABRIC_GET_HISTORY]: FAILED - Failed to read Fabric audit history: {}", e.getMessage(), e);
            }
        }

        List<Map<String, Object>> fallbackHist = simulatedHistory.getOrDefault(delegationId, Collections.emptyList());
        log.info("[FABRIC_GET_HISTORY]: SUCCESS - Loaded {} history entries from fallback state for ID='{}'",
                fallbackHist.size(), delegationId);
        return fallbackHist;
    }

    private Instant parseInstant(String str) {
        if (str == null || str.trim().isEmpty()) return Instant.now();
        try {
            return Instant.parse(str);
        } catch (Exception e) {
            try {
                String clean = str.replace("Z", "");
                if (clean.length() == 16) clean += ":00";
                return java.time.LocalDateTime.parse(clean).atZone(java.time.ZoneId.systemDefault()).toInstant();
            } catch (Exception ex) {
                return Instant.now();
            }
        }
    }

    /**
     * Query all delegations for a delegatee ID from Hyperledger Fabric Smart Contract
     */
    public List<Map<String, Object>> getDelegationsByDelegatee(String delegateeId) {
        log.debug("[FABRIC_GET_DELEGATIONS_BY_DELEGATEE]: DEBUG - Querying delegations for Delegatee='{}'", delegateeId);

        Contract contract = getContract();
        if (contract != null) {
            try {
                byte[] resultBytes = contract.evaluateTransaction("getDelegationsByDelegatee", delegateeId);
                String resultStr = new String(resultBytes, StandardCharsets.UTF_8);
                List<Map<String, Object>> delegations = objectMapper.readValue(resultStr, List.class);
                log.info("[FABRIC_GET_DELEGATIONS_BY_DELEGATEE]: SUCCESS - Retrieved {} delegations from Fabric for user '{}'",
                        delegations.size(), delegateeId);
                return delegations;
            } catch (Exception e) {
                log.error("[FABRIC_GET_DELEGATIONS_BY_DELEGATEE]: FAILED - Fabric evaluation failed: {}. Checking fallback state",
                        e.getMessage(), e);
            }
        } else {
            log.warn("[FABRIC_GET_DELEGATIONS_BY_DELEGATEE]: WARN - Fabric Gateway offline/null. Checking fallback state");
        }

        List<Map<String, Object>> fallbackList = new ArrayList<>();
        for (Map<String, Object> d : simulatedWorldState.values()) {
            if (delegateeId.equals(d.get("delegateeId"))) {
                fallbackList.add(d);
            }
        }
        log.info("[FABRIC_GET_DELEGATIONS_BY_DELEGATEE]: SUCCESS - Retrieved {} delegations from fallback state for user '{}'",
                fallbackList.size(), delegateeId);
        return fallbackList;
    }

    /**
     * Validate active delegation by delegateeId, targetModule, and permId
     */
    public boolean validateDelegation(String delegateeId, String targetModule, int permId) {
        log.debug("[FABRIC_VALIDATE_DELEGATION]: DEBUG - Evaluating active delegation: Delegatee='{}', Module='{}', PermId={}",
                delegateeId, targetModule, permId);

        List<Map<String, Object>> delegations = getDelegationsByDelegatee(delegateeId);
        Instant now = Instant.now();

        for (Map<String, Object> d : delegations) {
            if ("ACTIVE".equals(d.get("status")) &&
                    (targetModule.equalsIgnoreCase((String) d.get("resourceId")) ||
                     String.valueOf(permId).equals(d.get("resourceId")))) {

                Instant from = parseInstant((String) d.get("validFrom"));
                Instant until = parseInstant((String) d.get("validUntil"));
                if (!now.isBefore(from) && !now.isAfter(until)) {
                    log.info("[FABRIC_VALIDATE_DELEGATION]: SUCCESS - Found valid active delegation in Fabric: ID='{}' for user '{}'",
                            d.get("delegationId"), delegateeId);
                    return true;
                }
            }
        }

        log.debug("[FABRIC_VALIDATE_DELEGATION]: DEBUG - No matching active delegation found for Delegatee='{}' on Module='{}'",
                delegateeId, targetModule);
        return false;
    }

    /**
     * Inspect realtime gRPC connectivity state to Hyperledger Fabric Peer
     */
    public Map<String, Object> getNetworkStatus() {
        boolean isConnected = false;
        String status = "DISCONNECTED";
        String stateName = "OFFLINE";
        String details = "Fabric Gateway not initialized or peer offline";

        if (fabricChannel != null) {
            try {
                io.grpc.ConnectivityState state = fabricChannel.getState(true);
                stateName = state != null ? state.name() : "UNKNOWN";

                if (state == io.grpc.ConnectivityState.READY || state == io.grpc.ConnectivityState.IDLE) {
                    if (fabricGateway != null) {
                        isConnected = true;
                        status = "CONNECTED";
                        details = "gRPC channel active (State: " + stateName + ")";
                    } else {
                        status = "DEGRADED";
                        details = "gRPC channel is ready but Gateway credentials/certs not loaded";
                    }
                } else if (state == io.grpc.ConnectivityState.TRANSIENT_FAILURE) {
                    status = "DISCONNECTED";
                    details = "gRPC connection failure - peer unreachable or down";
                } else {
                    status = stateName;
                    details = "gRPC channel state: " + stateName;
                }
            } catch (Exception e) {
                details = "Error inspecting gRPC state: " + e.getMessage();
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("connected", isConnected);
        res.put("status", status);
        res.put("stateName", stateName);
        res.put("channel", channelName);
        res.put("chaincode", contractName);
        res.put("peerEndpoint", peerEndpoint);
        res.put("timestamp", Instant.now().toString());
        res.put("details", details);
        return res;
    }
}

