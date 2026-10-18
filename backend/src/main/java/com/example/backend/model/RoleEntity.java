package com.example.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleEntity {

    @Id
    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "role_name", length = 100, nullable = false)
    private String roleName;

    @Column(name = "kategori", length = 50, nullable = false)
    private String kategori;

    @Column(name = "status", length = 20)
    private String status;
}
