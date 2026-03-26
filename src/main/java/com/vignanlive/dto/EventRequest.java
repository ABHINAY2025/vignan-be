package com.vignanlive.dto;

import lombok.Data;

@Data
public class EventRequest {
    private String title;
    private String description;
    private String chiefGuest;
    private String startDate;
    private String endDate;
    private String startTime;
    private String endTime;
    private String venue;
    private Integer totalSeats;
    private Double price;
    private String posterImage; // base64
    private String status;
    private String audience;
    private Boolean promoted;
}
