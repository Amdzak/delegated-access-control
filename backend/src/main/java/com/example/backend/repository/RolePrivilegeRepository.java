package com.example.backend.repository;

import com.example.backend.model.RolePrivilegeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePrivilegeRepository extends JpaRepository<RolePrivilegeEntity, Long> {
    
    boolean existsByRoleIdAndPermIdAndIsGrantedTrue(Integer roleId, Integer permId);

    List<RolePrivilegeEntity> findByRoleIdAndIsGrantedTrue(Integer roleId);

    @Query("SELECT rp.permId FROM RolePrivilegeEntity rp WHERE rp.roleId = :roleId AND rp.isGranted = true")
    List<Integer> findGrantedPermIdsByRoleId(@Param("roleId") Integer roleId);
}
