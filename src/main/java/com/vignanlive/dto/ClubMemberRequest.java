package com.vignanlive.dto;

import lombok.Data;

import java.util.List;

@Data
public class ClubMemberRequest {
    private String email;
    private String password;
    private String clubId;
    private String clubName;
    private String department;
    private List<String> permissions;
}
