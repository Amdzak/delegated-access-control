package com.example.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "role_privileges")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePrivilegeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    @Column(name = "perm_id", nullable = false)
    private Integer permId;

    @Column(name = "is_granted", nullable = false)
    private Boolean isGranted;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private RoleEntity role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perm_id", insertable = false, updatable = false)
    private PrivilegeEntity privilege;
}
