package com.vignanlive.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EventResponse {
    private String id;
    private String title;
    private String description;
    private String chiefGuest;
    private String startDate;
    private String endDate;
    private String startTime;
    private String endTime;
    private String venue;
    private Integer totalSeats;
    private Integer bookedSeats;
    private Double price;
    private String posterImage;
    private String status;
    private String audience;
    private Boolean promoted;
    private String clubId;
    private String clubName;
    private String clubUpiId;
    private String department;
    private String createdByEmail;
    private String approvedBy;
    private Object approvedAt;
    private Object createdAt;
    private Integer interestedCount;
}
