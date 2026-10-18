package com.example.backend.repository;

import com.example.backend.model.PrivilegeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrivilegeRepository extends JpaRepository<PrivilegeEntity, Integer> {
    List<PrivilegeEntity> findByModuleId(Integer moduleId);
}
