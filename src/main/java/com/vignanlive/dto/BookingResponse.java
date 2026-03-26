package com.vignanlive.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookingResponse {
    private String id;
    private String userEmail;
    private String rollNumber;
    private String department;
    private String eventId;
    private String eventTitle;
    private String clubName;
    private String clubUpiId;
    private Double paidAmount;
    private String transactionId;
    private String paymentStatus;
    private Boolean attended;
    private Object attendedAt;
    private Object verifiedAt;
    private String verifiedBy;
    private Object createdAt;
}
