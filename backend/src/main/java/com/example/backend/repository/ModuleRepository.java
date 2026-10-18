package com.example.backend.repository;

import com.example.backend.model.ModuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModuleRepository extends JpaRepository<ModuleEntity, Integer> {
    Optional<ModuleEntity> findByModuleName(String moduleName);
}
