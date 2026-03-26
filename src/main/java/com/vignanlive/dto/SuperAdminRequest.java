package com.vignanlive.dto;

import lombok.Data;

@Data
public class SuperAdminRequest {
    private String email;
    private String password;
    private String department;
}
