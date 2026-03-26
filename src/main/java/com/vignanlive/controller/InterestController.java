package com.vignanlive.controller;

import com.vignanlive.security.FirebaseUserDetails;
import com.vignanlive.service.InterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class InterestController {

    private final InterestService interestService;

    @PostMapping("/{eventId}/interest")
    public ResponseEntity<Map<String, Object>> toggleInterest(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String eventId) {
        boolean interested = interestService.toggleInterest(eventId, user.getEmail());
        return ResponseEntity.ok(Map.of("interested", interested));
    }

    @GetMapping("/{eventId}/interest")
    public ResponseEntity<Map<String, Object>> checkInterest(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String eventId) {
        boolean interested = interestService.isInterested(eventId, user.getEmail());
        return ResponseEntity.ok(Map.of("interested", interested));
    }
}
