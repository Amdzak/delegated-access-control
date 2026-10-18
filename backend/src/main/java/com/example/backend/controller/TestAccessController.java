package com.example.backend.controller;

import com.example.backend.dto.response.ApiResponse;
import com.example.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/test-access")
@Slf4j
@Tag(name = "4. Protected Resources Simulation", description = "Test hospital feature access with two-tier authorization: Static RBAC (Tier 1) and Fabric DAC (Tier 2)")
public class TestAccessController {

    @PostMapping("/logistik-pemesanan")
    @PreAuthorize("hasPermission('Logistik', 31)")
    @Operation(summary = "Access Test: Logistics Medicine Ordering (Perm ID 31)", description = "Original RBAC owner: Pharmacy/Admin. Accessible by delegatee if active delegation exists.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executePemesananLogistik(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody(required = false) Map<String, Object> body) {
        log.debug("[SIMULATION_LOGISTICS_CONTROLLER]: DEBUG - Evaluating logistics access for user: '{}' (Role={})",
                user.getUsername(), user.getRoleName());
        try {
            log.info("[SIMULATION_LOGISTICS_CONTROLLER]: SUCCESS - Access GRANTED for user '{}' ({}), logistics order processed",
                    user.getUsername(), user.getRoleName());
            return ResponseEntity.ok(ApiResponse.ok("ACCESS GRANTED: Logistics Medicine Order Form successfully processed!", Map.of(
                    "executedBy", user.getFullName(),
                    "role", user.getRoleName(),
                    "operation", "Logistics Ordering",
                    "moduleId", 5,
                    "permId", 31,
                    "status", "SUCCESS"
            )));
        } catch (Exception ex) {
            log.error("[SIMULATION_LOGISTICS_CONTROLLER]: FAILED - Logistics access denied for user '{}': {}",
                    user.getUsername(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @PostMapping("/transaksi-rawat-jalan")
    @PreAuthorize("hasPermission('Transaksi', 1202)")
    @Operation(summary = "Access Test: Outpatient Clinic Action (Perm ID 1202)", description = "Original RBAC owner: Doctor/Admin. Accessible by Nurse if active delegation exists.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeRawatJalan(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody(required = false) Map<String, Object> body) {
        log.debug("[SIMULATION_OUTPATIENT_CONTROLLER]: DEBUG - Evaluating outpatient access for user: '{}' (Role={})",
                user.getUsername(), user.getRoleName());
        try {
            log.info("[SIMULATION_OUTPATIENT_CONTROLLER]: SUCCESS - Access GRANTED for user '{}' ({}), outpatient clinical entry processed",
                    user.getUsername(), user.getRoleName());
            return ResponseEntity.ok(ApiResponse.ok("ACCESS GRANTED: Outpatient Clinical Advice and Action successfully processed!", Map.of(
                    "executedBy", user.getFullName(),
                    "role", user.getRoleName(),
                    "operation", "Outpatient Clinical Action",
                    "permId", 1202,
                    "status", "SUCCESS"
            )));
        } catch (Exception ex) {
            log.error("[SIMULATION_OUTPATIENT_CONTROLLER]: FAILED - Outpatient access denied for user '{}': {}",
                    user.getUsername(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @PostMapping("/rekam-medis-baca")
    @PreAuthorize("hasPermission('Rekam Medis', 1420)")
    @Operation(summary = "Access Test: Read Medical Records (Perm ID 1420)", description = "Original RBAC owner: Doctor/Medical Records. Accessible by Nurse if delegated by attending physician.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeBacaRekamMedis(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody(required = false) Map<String, Object> body) {
        log.debug("[SIMULATION_EMR_CONTROLLER]: DEBUG - Evaluating medical record access for user: '{}' (Role={})",
                user.getUsername(), user.getRoleName());
        try {
            log.info("[SIMULATION_EMR_CONTROLLER]: SUCCESS - Access GRANTED for user '{}' ({}), patient medical record opened",
                    user.getUsername(), user.getRoleName());
            return ResponseEntity.ok(ApiResponse.ok("ACCESS GRANTED: Patient Medical Records successfully accessed!", Map.of(
                    "executedBy", user.getFullName(),
                    "role", user.getRoleName(),
                    "operation", "Read Patient Medical Records",
                    "permId", 1420,
                    "patientMock", Map.of(
                            "noRm", "RM-2026-0042",
                            "patientName", "Mr. Santoso (54 yo)",
                            "primaryDiagnosis", "Functional Dyspepsia + Grade 2 HT",
                            "latestTherapy", "Omeprazole 20mg IV, Amlodipine 10mg PO"
                    ),
                    "status", "SUCCESS"
            )));
        } catch (Exception ex) {
            log.error("[SIMULATION_EMR_CONTROLLER]: FAILED - Medical record access denied for user '{}': {}",
                    user.getUsername(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
