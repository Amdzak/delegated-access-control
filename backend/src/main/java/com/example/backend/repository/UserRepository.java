package com.example.backend.repository;

import com.example.backend.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByUserId(String userId);
    boolean existsByUsername(String username);
    List<UserEntity> findByRoleId(Integer roleId);
    List<UserEntity> findByRoleIdAndIsActiveTrue(Integer roleId);
    List<UserEntity> findAllByOrderByFullNameAsc();
}
