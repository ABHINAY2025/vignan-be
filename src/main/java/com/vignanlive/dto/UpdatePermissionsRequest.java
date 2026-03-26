package com.vignanlive.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdatePermissionsRequest {
    private List<String> permissions;
}
