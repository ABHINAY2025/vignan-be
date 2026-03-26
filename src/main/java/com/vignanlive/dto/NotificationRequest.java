package com.vignanlive.dto;

import lombok.Data;

import java.util.List;

@Data
public class NotificationRequest {
    private String title;
    private String body;

    /**
     * Channel for sound: "booking", "event", "warning", "reminder"
     */
    private String channel;

    /**
     * Audience target:
     * "all" — all users
     * "department" — users in targetDepartment
     * "event" — users who booked targetEventId
     * "emails" — specific email list
     */
    private String audience;

    private String targetDepartment;
    private String targetEventId;
    private List<String> targetEmails;

    /**
     * Optional deep link data
     */
    private String screen;  // "EventDetail", "BookingTicket", etc.
    private String screenId; // eventId or bookingId
}
