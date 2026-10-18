package com.example.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDelegationRequest {

    @NotBlank(message = "Delegatee ID is required")
    @Schema(description = "User ID of the delegation recipient (delegatee)", example = "USR-02101")
    private String delegateeId;

    @NotBlank(message = "Target module / resource is required")
    @Schema(description = "Target module or resource being delegated", example = "Logistik")
    private String targetModule;

    @NotNull(message = "Permission ID (permId) is required")
    @Schema(description = "Specific permission ID on the hospital system", example = "31")
    private Integer permId;

    @Builder.Default
    @NotBlank(message = "Permission type is required (READ, WRITE, or READ_WRITE)")
    @Schema(description = "Permission access level", example = "READ_WRITE")
    private String permission = "READ_WRITE";

    @NotNull(message = "Delegation start time (validFrom) is required")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(type = "string", pattern = "yyyy-MM-dd HH:mm:ss", example = "2026-10-18 20:00:00", description = "Delegation start time in format yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validFrom;

    @NotNull(message = "Delegation end time (validUntil) is required")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(type = "string", pattern = "yyyy-MM-dd HH:mm:ss", example = "2026-10-19 20:00:00", description = "Delegation expiration time in format yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validUntil;

    @NotBlank(message = "Justification / reason is required")
    @Schema(description = "Reason or justification for delegating access authority", example = "Emergency coverage for inpatient care")
    private String justification;

    @Schema(description = "Parent delegation ID (optional, empty for root delegation)", example = "")
    private String parentDelegationId;

    @Builder.Default
    @Schema(description = "Maximum delegation chain depth (default: 2)", example = "2")
    private Integer maxDepth = 2;
}
