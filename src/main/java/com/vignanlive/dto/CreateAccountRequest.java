package com.vignanlive.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateAccountRequest {
    private String email;
    private String password;
    private String role;
    private String department;
    private String clubId;
    private String clubName;
    private String upiId;
    private List<String> permissions;
}
