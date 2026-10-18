package com.example.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private String userId;
    private String username;
    private String fullName;
    private String email;
    private Integer roleId;
    private String roleName;
    private String unitCabang;
    private List<Integer> grantedPermIds;
}
