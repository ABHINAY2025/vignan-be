package com.vignanlive.dto;

import lombok.Data;

@Data
public class ChangeHeadRequest {
    private String newHeadEmail;
    private String newHeadPassword;
}
