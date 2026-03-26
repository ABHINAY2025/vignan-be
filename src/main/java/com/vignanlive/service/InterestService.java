package com.vignanlive.service;

import com.google.cloud.firestore.*;
import com.vignanlive.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterestService {

    private final Firestore firestore;

    /**
     * Toggle interest on an event. If the user already expressed interest, remove it.
     * Otherwise, add it. Updates interestedCount on the event doc atomically.
     *
     * @return true if now interested, false if interest was removed
     */
    public boolean toggleInterest(String eventId, String userEmail) {
        String docId = eventId + "_" + userEmail.replaceAll("[^a-zA-Z0-9]", "_");
        DocumentReference interestRef = firestore.collection("event_interests").document(docId);
        DocumentReference eventRef = firestore.collection("events").document(eventId);

        try {
            // Check if event exists
            DocumentSnapshot eventDoc = eventRef.get().get();
            if (!eventDoc.exists()) {
                throw ApiException.notFound("Event not found");
            }

            // Check if already interested
            DocumentSnapshot existing = interestRef.get().get();

            if (existing.exists()) {
                // Remove interest
                WriteBatch batch = firestore.batch();
                batch.delete(interestRef);
                batch.update(eventRef, "interestedCount", FieldValue.increment(-1));
                batch.commit().get();
                return false;
            } else {
                // Add interest
                Map<String, Object> data = new HashMap<>();
                data.put("eventId", eventId);
                data.put("userEmail", userEmail);
                data.put("createdAt", FieldValue.serverTimestamp());

                WriteBatch batch = firestore.batch();
                batch.set(interestRef, data);
                batch.update(eventRef, "interestedCount", FieldValue.increment(1));
                batch.commit().get();
                return true;
            }

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to toggle interest");
        }
    }

    /**
     * Check if a user is interested in an event.
     */
    public boolean isInterested(String eventId, String userEmail) {
        String docId = eventId + "_" + userEmail.replaceAll("[^a-zA-Z0-9]", "_");
        try {
            return firestore.collection("event_interests").document(docId).get().get().exists();
        } catch (InterruptedException | ExecutionException e) {
            return false;
        }
    }
}
