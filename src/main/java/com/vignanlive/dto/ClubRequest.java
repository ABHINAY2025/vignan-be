package com.vignanlive.dto;

import lombok.Data;

@Data
public class ClubRequest {
    private String name;
    private String department;
    private String headEmail;
    private String headPassword;
    private String upiId;
    private String description;
}
