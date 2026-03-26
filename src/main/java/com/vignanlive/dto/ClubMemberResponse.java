package com.vignanlive.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ClubMemberResponse {
    private String email;
    private String clubId;
    private String clubName;
    private String department;
    private List<String> permissions;
    private String addedBy;
    private Object createdAt;
}
