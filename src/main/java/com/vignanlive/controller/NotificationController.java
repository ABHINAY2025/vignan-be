package com.vignanlive.controller;

import com.google.cloud.firestore.*;
import com.vignanlive.dto.NotificationRequest;
import com.vignanlive.exception.ApiException;
import com.vignanlive.security.FirebaseUserDetails;
import com.vignanlive.service.NotificationService;
import com.vignanlive.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final int MAX_CUSTOM_PER_DAY = 5;

    private final NotificationService notificationService;
    private final Firestore firestore;

    /**
     * Send a custom notification.
     * Only Super Admin, Master Admin, or Club Head can send.
     * Limited to 5 custom notifications per sender per day.
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendNotification(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @RequestBody NotificationRequest request) {

        if (!Constants.hasMinRole(user.getRole(), Constants.ROLE_CLUB_HEAD)) {
            throw ApiException.forbidden("Only club heads and above can send notifications");
        }

        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw ApiException.badRequest("Title is required");
        }
        if (request.getBody() == null || request.getBody().isBlank()) {
            throw ApiException.badRequest("Body is required");
        }

        // Check daily limit
        try {
            ZonedDateTime startOfDay = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDate()
                    .atStartOfDay(ZoneId.of("Asia/Kolkata"));
            Date startDate = Date.from(startOfDay.toInstant());

            List<QueryDocumentSnapshot> todaySent = firestore.collection(Constants.COL_NOTIFICATIONS)
                    .whereEqualTo("sentBy", user.getEmail())
                    .whereGreaterThan("createdAt", startDate)
                    .get().get().getDocuments();

            if (todaySent.size() >= MAX_CUSTOM_PER_DAY) {
                throw ApiException.badRequest(
                        "Daily limit reached (" + MAX_CUSTOM_PER_DAY + " custom notifications per day)");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // If limit check fails, still allow — don't block on non-critical error
        }

        int sent = notificationService.sendCustomNotification(request, user.getEmail());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "sent", sent
        ));
    }
}
