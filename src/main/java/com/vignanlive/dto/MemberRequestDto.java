package com.vignanlive.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MemberRequestDto {
    private String id;
    private String type; // remove_member, revoke_permission
    private String memberEmail;
    private String clubId;
    private String clubName;
    private String department;
    private List<String> permissionsToRevoke;
    private String reason;
    private String requestedBy;
    private String status; // pending, approved, rejected
    private String reviewedBy;
    private Object reviewedAt;
    private Object createdAt;
}
