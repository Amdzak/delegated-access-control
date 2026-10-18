package com.example.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokeDelegationRequest {

    @Schema(description = "Reason for delegation revocation", example = "Emergency duty concluded")
    private String reason;
}
