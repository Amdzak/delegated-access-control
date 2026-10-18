package com.example.backend.repository;

import com.example.backend.model.DelegationAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DelegationAuditRepository extends JpaRepository<DelegationAuditEntity, Long> {
    List<DelegationAuditEntity> findByDelegationIdOrderByCreatedAtDesc(String delegationId);
    List<DelegationAuditEntity> findByDelegatorIdOrderByCreatedAtDesc(String delegatorId);
    List<DelegationAuditEntity> findByDelegateeIdOrderByCreatedAtDesc(String delegateeId);
    List<DelegationAuditEntity> findAllByOrderByCreatedAtDesc();
}
