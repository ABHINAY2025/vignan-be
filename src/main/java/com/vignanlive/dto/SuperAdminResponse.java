package com.vignanlive.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SuperAdminResponse {
    private String email;
    private String department;
    private String addedBy;
    private Object createdAt;
}
