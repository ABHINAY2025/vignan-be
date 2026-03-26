package com.vignanlive.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClubResponse {
    private String id;
    private String name;
    private String department;
    private String headEmail;
    private String upiId;
    private String description;
    private String createdBy;
    private Object createdAt;
}
