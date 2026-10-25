package com.example.backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "delegation_audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelegationAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "delegation_id", length = 100, nullable = false)
    private String delegationId;

    @Column(name = "parent_delegation_id", length = 100)
    private String parentDelegationId;

    @Column(name = "depth")
    private Integer depth;

    @Column(name = "delegator_id", length = 50, nullable = false)
    private String delegatorId;

    @Column(name = "delegatee_id", length = 50, nullable = false)
    private String delegateeId;

    @Column(name = "target_module", length = 100, nullable = false)
    private String targetModule;

    @Column(name = "perm_id", nullable = false)
    private Integer permId;

    @Column(name = "action_type", length = 30, nullable = false)
    private String actionType;

    @Column(name = "tx_id", length = 128, nullable = false)
    private String txId;

    @Column(name = "justification", columnDefinition = "NVARCHAR(MAX)", nullable = false)
    private String justification;

    @Column(name = "valid_from", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validUntil;

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
