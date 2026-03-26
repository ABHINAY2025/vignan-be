package com.vignanlive.dto;

import lombok.Data;

@Data
public class BookingRequest {
    private String eventId;
    private String transactionId; // UTR number, null for free events
}
