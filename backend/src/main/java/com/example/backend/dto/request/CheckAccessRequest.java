package com.example.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckAccessRequest {

    @NotBlank(message = "Delegation ID is required")
    @Schema(description = "Unique delegation ID", example = "DEL-20261018-001")
    private String delegationId;

    @NotBlank(message = "Delegatee ID is required")
    @Schema(description = "User ID of the access requester", example = "USR-02101")
    private String requesterDelegateeId;

    @NotBlank(message = "Resource ID is required")
    @Schema(description = "Target resource or module name", example = "Logistik")
    private String resourceId;

    @NotBlank(message = "Permission is required (READ, WRITE, or READ_WRITE)")
    @Schema(description = "Requested permission level", example = "READ")
    private String permission;
}
