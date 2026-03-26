package com.vignanlive.controller;

import com.vignanlive.dto.EventRequest;
import com.vignanlive.dto.EventResponse;
import com.vignanlive.exception.ApiException;
import com.vignanlive.security.FirebaseUserDetails;
import com.vignanlive.service.EventService;
import com.vignanlive.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventResponse>> getApprovedEvents(
            @AuthenticationPrincipal FirebaseUserDetails user) {
        return ResponseEntity.ok(
                eventService.getApprovedEvents(user.getDepartment(), user.getRole()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable String id) {
        return ResponseEntity.ok(eventService.getEvent(id));
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.createEvent(request, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String id,
            @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(id, request, user));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<EventResponse>> getPendingEvents(
            @AuthenticationPrincipal FirebaseUserDetails user) {
        if (!Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Only super admins can view pending events");
        }
        return ResponseEntity.ok(eventService.getPendingEvents(user.getDepartment()));
    }

    @GetMapping("/my")
    public ResponseEntity<List<EventResponse>> getMyEvents(
            @AuthenticationPrincipal FirebaseUserDetails user) {
        if (!Constants.ROLE_CLUB_HEAD.equals(user.getRole())) {
            throw ApiException.forbidden("Only club heads can view their events");
        }
        return ResponseEntity.ok(eventService.getMyEvents(user.getEmail()));
    }

    @GetMapping("/club/{clubId}")
    public ResponseEntity<List<EventResponse>> getEventsByClub(@PathVariable String clubId) {
        return ResponseEntity.ok(eventService.getEventsByClub(clubId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String id) {
        eventService.deleteEvent(id, user);
        return ResponseEntity.noContent().build();
    }
}
