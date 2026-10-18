package com.example.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "privileges")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivilegeEntity {

    @Id
    @Column(name = "perm_id")
    private Integer permId;

    @Column(name = "module_id", nullable = false)
    private Integer moduleId;

    @Column(name = "sub_privilege_name", length = 200, nullable = false)
    private String subPrivilegeName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", insertable = false, updatable = false)
    private ModuleEntity module;
}
