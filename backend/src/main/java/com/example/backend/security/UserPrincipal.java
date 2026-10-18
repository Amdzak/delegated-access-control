package com.example.backend.security;

import com.example.backend.model.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final String userId;
    private final String username;
    private final String password;
    private final String fullName;
    private final Integer roleId;
    private final String roleName;
    private final String unitCabang;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(UserEntity user) {
        String roleName = user.getRole() != null ? user.getRole().getRoleName() : "ROLE_USER";
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase().replace(" ", "_"));

        return new UserPrincipal(
                user.getUserId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getFullName(),
                user.getRoleId(),
                roleName,
                user.getUnitCabang(),
                Boolean.TRUE.equals(user.getIsActive()),
                Collections.singletonList(authority)
        );
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
