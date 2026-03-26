package com.vignanlive.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthResponse {
    private String email;
    private String role;
    private String department;
    private String clubId;
    private String clubName;
    private String upiId;
    private List<String> permissions;
    private String rollNumber;
}
