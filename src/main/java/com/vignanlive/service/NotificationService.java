package com.vignanlive.service;

import com.google.cloud.firestore.*;
import com.vignanlive.dto.NotificationRequest;
import com.vignanlive.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final Firestore firestore;

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    // ─── High-level methods for automatic notifications ───

    /**
     * Notify a single user by email.
     */
    public void notifyUser(String email, String title, String body, String channel,
                           String screen, String screenId) {
        List<String> tokens = getTokensForEmails(List.of(email));
        if (tokens.isEmpty()) return;
        sendToTokens(tokens, title, body, channel, screen, screenId);
        saveNotification(email, title, body, channel, screen, screenId);
    }

    /**
     * Notify all users in a department.
     */
    public void notifyDepartment(String department, String title, String body,
                                  String channel, String screen, String screenId) {
        List<String> tokens = getTokensForDepartment(department);
        if (tokens.isEmpty()) return;
        sendToTokens(tokens, title, body, channel, screen, screenId);
        saveNotificationBulk(department, title, body, channel);
    }

    /**
     * Notify all registered users.
     */
    public void notifyAll(String title, String body, String channel,
                          String screen, String screenId) {
        List<String> tokens = getAllTokens();
        if (tokens.isEmpty()) return;
        sendToTokens(tokens, title, body, channel, screen, screenId);
        saveNotificationBulk("all", title, body, channel);
    }

    /**
     * Notify all students who booked a specific event.
     */
    public void notifyEventAttendees(String eventId, String title, String body,
                                      String channel, String screen, String screenId) {
        try {
            List<QueryDocumentSnapshot> bookings = firestore.collection(Constants.COL_BOOKINGS)
                    .whereEqualTo("eventId", eventId)
                    .get().get().getDocuments();

            List<String> emails = bookings.stream()
                    .map(d -> d.getString("userEmail"))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            if (emails.isEmpty()) return;
            List<String> tokens = getTokensForEmails(emails);
            if (tokens.isEmpty()) return;
            sendToTokens(tokens, title, body, channel, screen, screenId);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to notify event attendees", e);
        }
    }

    /**
     * Send a custom notification from admin/club head.
     */
    public int sendCustomNotification(NotificationRequest req, String senderEmail) {
        List<String> tokens;

        switch (req.getAudience() != null ? req.getAudience() : "all") {
            case "department":
                tokens = getTokensForDepartment(req.getTargetDepartment());
                break;
            case "event":
                tokens = getTokensForEventAttendees(req.getTargetEventId());
                break;
            case "emails":
                tokens = getTokensForEmails(req.getTargetEmails());
                break;
            default:
                tokens = getAllTokens();
                break;
        }

        // Exclude sender's own tokens so they don't receive their own notification
        if (senderEmail != null) {
            List<String> senderTokens = getTokensForEmails(List.of(senderEmail.toLowerCase()));
            List<String> filtered = new ArrayList<>(tokens);
            filtered.removeAll(senderTokens);
            if (!filtered.isEmpty()) {
                tokens = filtered;
            }
        }

        if (tokens.isEmpty()) return 0;

        String channel = req.getChannel() != null ? req.getChannel() : "event";
        sendToTokens(tokens, req.getTitle(), req.getBody(), channel,
                req.getScreen(), req.getScreenId());

        // Save to notifications collection for history
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("title", req.getTitle());
            data.put("body", req.getBody());
            data.put("channel", channel);
            data.put("audience", req.getAudience());
            data.put("targetDepartment", req.getTargetDepartment());
            data.put("targetEventId", req.getTargetEventId());
            data.put("sentBy", senderEmail);
            data.put("recipientCount", tokens.size());
            data.put("createdAt", FieldValue.serverTimestamp());
            firestore.collection(Constants.COL_NOTIFICATIONS).add(data);
        } catch (Exception e) {
            log.error("Failed to save notification history", e);
        }

        return tokens.size();
    }

    // ─── Expo Push API sending ───

    private void sendToTokens(List<String> tokens, String title, String body,
                               String channel, String screen, String screenId) {
        if (tokens.isEmpty()) return;

        // Filter to only Expo push tokens — old FCM tokens won't work with Expo Push API
        List<String> expoTokens = tokens.stream()
                .filter(t -> t != null && t.startsWith("ExponentPushToken"))
                .collect(Collectors.toList());

        if (expoTokens.isEmpty()) {
            log.warn("No Expo push tokens found (have {} non-Expo tokens)", tokens.size());
            return;
        }

        String sound = getSoundForChannel(channel);
        String channelId = channel != null ? channel : "event";

        // Send in batches of 100 (Expo push API recommendation)
        for (int i = 0; i < expoTokens.size(); i += 100) {
            List<String> batch = expoTokens.subList(i, Math.min(i + 100, expoTokens.size()));

            // Build JSON array of messages for Expo Push API
            StringBuilder jsonArray = new StringBuilder("[");
            for (int j = 0; j < batch.size(); j++) {
                if (j > 0) jsonArray.append(",");
                jsonArray.append(buildExpoPushMessage(
                        batch.get(j), title, body, channelId, sound, screen, screenId));
            }
            jsonArray.append("]");

            try {
                URL url = new URL(EXPO_PUSH_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                byte[] payload = jsonArray.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                int status = conn.getResponseCode();
                // Read response body for debugging
                java.io.InputStream is = (status == 200) ? conn.getInputStream() : conn.getErrorStream();
                String responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                is.close();

                if (status == 200) {
                    log.info("Expo Push API: sent {} notifications. Response: {}", batch.size(), responseBody);
                } else {
                    log.error("Expo Push API status {}: {}", status, responseBody);
                }
                conn.disconnect();
            } catch (Exception e) {
                log.error("Expo Push API send failed", e);
            }
        }
    }

    private String buildExpoPushMessage(String token, String title, String body,
                                         String channelId, String sound,
                                         String screen, String screenId) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"to\":").append(jsonStr(token));
        sb.append(",\"title\":").append(jsonStr(title != null ? title : ""));
        sb.append(",\"body\":").append(jsonStr(body != null ? body : ""));
        sb.append(",\"sound\":").append(jsonStr(sound));
        sb.append(",\"channelId\":").append(jsonStr(channelId));
        sb.append(",\"priority\":\"high\"");

        // Data payload for tap navigation
        sb.append(",\"data\":{");
        sb.append("\"screen\":").append(jsonStr(screen != null ? screen : ""));
        sb.append(",\"screenId\":").append(jsonStr(screenId != null ? screenId : ""));
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    private String jsonStr(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String getSoundForChannel(String channel) {
        if (channel == null) return "event.wav";
        switch (channel) {
            case "booking": return "booking.wav";
            case "warning": return "warning.wav";
            case "reminder": return "reminder.wav";
            default: return "event.wav";
        }
    }

    // ─── Token fetching ───

    private List<String> getAllTokens() {
        try {
            return firestore.collection(Constants.COL_USER_DEVICES)
                    .get().get().getDocuments().stream()
                    .map(d -> d.getString("pushToken"))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to fetch all tokens", e);
            return List.of();
        }
    }

    private List<String> getTokensForEmails(List<String> emails) {
        if (emails == null || emails.isEmpty()) return List.of();
        try {
            List<String> allTokens = new ArrayList<>();
            for (int i = 0; i < emails.size(); i += 30) {
                List<String> batch = emails.subList(i, Math.min(i + 30, emails.size()));
                List<QueryDocumentSnapshot> docs = firestore.collection(Constants.COL_USER_DEVICES)
                        .whereIn("email", batch)
                        .get().get().getDocuments();
                docs.stream()
                        .map(d -> d.getString("pushToken"))
                        .filter(Objects::nonNull)
                        .forEach(allTokens::add);
            }
            return allTokens.stream().distinct().collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to fetch tokens for emails", e);
            return List.of();
        }
    }

    private List<String> getTokensForDepartment(String department) {
        if (department == null) return List.of();
        try {
            List<String> emails = firestore.collection(Constants.COL_USERS)
                    .whereEqualTo("department", department)
                    .get().get().getDocuments().stream()
                    .map(d -> d.getString("email"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return getTokensForEmails(emails);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to fetch dept tokens", e);
            return List.of();
        }
    }

    private List<String> getTokensForEventAttendees(String eventId) {
        if (eventId == null) return List.of();
        try {
            List<String> emails = firestore.collection(Constants.COL_BOOKINGS)
                    .whereEqualTo("eventId", eventId)
                    .get().get().getDocuments().stream()
                    .map(d -> d.getString("userEmail"))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            return getTokensForEmails(emails);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to fetch event attendee tokens", e);
            return List.of();
        }
    }

    // ─── Notification history ───

    private void saveNotification(String email, String title, String body,
                                   String channel, String screen, String screenId) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("userEmail", email);
            data.put("title", title);
            data.put("body", body);
            data.put("channel", channel);
            data.put("screen", screen);
            data.put("screenId", screenId);
            data.put("read", false);
            data.put("createdAt", FieldValue.serverTimestamp());
            firestore.collection(Constants.COL_NOTIFICATIONS).add(data);
        } catch (Exception e) {
            log.error("Failed to save notification", e);
        }
    }

    private void saveNotificationBulk(String audience, String title, String body, String channel) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("audience", audience);
            data.put("title", title);
            data.put("body", body);
            data.put("channel", channel);
            data.put("read", false);
            data.put("createdAt", FieldValue.serverTimestamp());
            firestore.collection(Constants.COL_NOTIFICATIONS).add(data);
        } catch (Exception e) {
            log.error("Failed to save bulk notification", e);
        }
    }
}
