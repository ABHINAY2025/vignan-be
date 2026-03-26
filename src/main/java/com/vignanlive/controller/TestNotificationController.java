package com.vignanlive.controller;

import com.google.cloud.firestore.*;
import com.vignanlive.service.NotificationService;
import com.vignanlive.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Public test endpoints for debugging notifications (no auth required).
 * Remove this controller in production.
 */
@Slf4j
@RestController
@RequestMapping("/api/test-notifications")
@RequiredArgsConstructor
public class TestNotificationController {

    private final Firestore firestore;
    private final NotificationService notificationService;

    /**
     * List all registered device tokens.
     * GET /api/test-notifications/tokens
     */
    @GetMapping("/tokens")
    public ResponseEntity<Map<String, Object>> listTokens() throws Exception {
        List<QueryDocumentSnapshot> docs = firestore.collection(Constants.COL_USER_DEVICES)
                .get().get().getDocuments();

        List<Map<String, Object>> devices = docs.stream().map(d -> {
            Map<String, Object> device = new HashMap<>();
            device.put("docId", d.getId());
            device.put("email", d.getString("email"));
            device.put("pushToken", d.getString("pushToken"));
            device.put("platform", d.getString("platform"));
            return device;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "count", devices.size(),
                "devices", devices
        ));
    }

    /**
     * Send test notification to all registered devices via Expo Push API.
     * POST /api/test-notifications/send-all
     * Body: { "title": "Test", "body": "Hello everyone", "screen": "EventDetail", "screenId": "abc123" }
     */
    @PostMapping("/send-all")
    public ResponseEntity<Map<String, Object>> sendAll(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "Test Notification");
        String msgBody = body.getOrDefault("body", "This is a test broadcast");
        String screen = body.get("screen");
        String screenId = body.get("screenId");
        String channel = body.getOrDefault("channel", "event");

        try {
            notificationService.notifyAll(title, msgBody, channel, screen, screenId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Notification sent via Expo Push API"
            ));
        } catch (Exception e) {
            log.error("Test send-all failed", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Send test notification to a specific user by email.
     * POST /api/test-notifications/send-user
     * Body: { "email": "user@vbithyd.ac.in", "title": "Test", "body": "Hello", "screen": "EventDetail", "screenId": "abc" }
     */
    @PostMapping("/send-user")
    public ResponseEntity<Map<String, Object>> sendUser(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String title = body.getOrDefault("title", "Test Notification");
        String msgBody = body.getOrDefault("body", "This is a test");
        String screen = body.get("screen");
        String screenId = body.get("screenId");
        String channel = body.getOrDefault("channel", "event");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }

        try {
            notificationService.notifyUser(email.toLowerCase(), title, msgBody, channel, screen, screenId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Notification sent to " + email
            ));
        } catch (Exception e) {
            log.error("Test send-user failed", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}
