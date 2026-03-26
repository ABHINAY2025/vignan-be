package com.vignanlive.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
public class FirebaseUserDetails implements UserDetails {

    private final String email;
    private final String role;
    private final Map<String, Object> profile;

    public FirebaseUserDetails(String email, String role, Map<String, Object> profile) {
        this.email = email;
        this.role = role;
        this.profile = profile != null ? profile : Collections.emptyMap();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    public String getDepartment() {
        return (String) profile.get("department");
    }

    public String getClubId() {
        return (String) profile.get("clubId");
    }

    public String getClubName() {
        return (String) profile.get("clubName");
    }

    public String getUpiId() {
        return (String) profile.get("upiId");
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissions() {
        Object perms = profile.get("permissions");
        if (perms instanceof List) {
            return (List<String>) perms;
        }
        return Collections.emptyList();
    }

    public boolean hasPermission(String permission) {
        return getPermissions().contains(permission);
    }
}
