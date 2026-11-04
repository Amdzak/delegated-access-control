package com.example.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {
    private String userId;
    private String username;
    private String fullName;
    private String email;
    private Integer roleId;
    private String roleName;
    private String unitCabang;
    private Boolean isActive;
}
