package com.example.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelegationResponse {
    private String delegationId;
    private String delegatorId;
    private String delegatorName;
    private String delegateeId;
    private String delegateeName;
    private String targetModule;
    private Integer permId;
    private String permission;
    private String status;
    private String txId;
    private String justification;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validFrom;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validUntil;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private Integer depth;
    private Integer maxDepth;
    private String parentDelegationId;
}
