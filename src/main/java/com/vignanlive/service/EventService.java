package com.vignanlive.service;

import com.google.cloud.firestore.*;
import com.vignanlive.dto.EventRequest;
import com.vignanlive.dto.EventResponse;
import com.vignanlive.exception.ApiException;
import com.vignanlive.security.FirebaseUserDetails;
import com.vignanlive.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final Firestore firestore;
    private final NotificationService notificationService;

    public List<EventResponse> getApprovedEvents(String userDepartment, String userRole) {
        try {
            Query query = firestore.collection(Constants.COL_EVENTS)
                    .whereEqualTo("status", Constants.STATUS_APPROVED)
                    .orderBy("createdAt", Query.Direction.DESCENDING);

            List<QueryDocumentSnapshot> docs = query.get().get().getDocuments();
            List<EventResponse> events = new ArrayList<>();

            for (QueryDocumentSnapshot doc : docs) {
                String audience = doc.getString("audience");
                String eventDept = doc.getString("department");

                // Students see "all" events + events matching their department
                // Admins/heads see everything
                if (!Constants.ROLE_STUDENT.equals(userRole)
                        || Constants.AUDIENCE_ALL.equals(audience)
                        || (eventDept != null && eventDept.equals(userDepartment))) {
                    events.add(toEventResponse(doc));
                }
            }
            return events;

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch events");
        }
    }

    public EventResponse getEvent(String eventId) {
        try {
            DocumentSnapshot doc = firestore.collection(Constants.COL_EVENTS)
                    .document(eventId).get().get();
            if (!doc.exists()) {
                throw ApiException.notFound("Event not found");
            }
            return toEventResponse(doc);
        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch event");
        }
    }

    public EventResponse createEvent(EventRequest request, FirebaseUserDetails user) {
        if (!Constants.ROLE_CLUB_HEAD.equals(user.getRole())) {
            throw ApiException.forbidden("Only club heads can create events");
        }

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("title", request.getTitle());
            data.put("description", request.getDescription());
            data.put("chiefGuest", request.getChiefGuest());
            data.put("startDate", request.getStartDate());
            data.put("endDate", request.getEndDate());
            data.put("startTime", request.getStartTime());
            data.put("endTime", request.getEndTime());
            data.put("venue", request.getVenue());
            data.put("totalSeats", request.getTotalSeats());
            data.put("bookedSeats", 0);
            data.put("price", request.getPrice());
            data.put("posterImage", request.getPosterImage());
            data.put("status", Constants.STATUS_PENDING);
            data.put("audience", Constants.AUDIENCE_ALL);
            data.put("clubId", user.getClubId());
            data.put("clubName", user.getClubName());
            data.put("clubUpiId", user.getUpiId());
            data.put("department", user.getDepartment());
            data.put("createdByEmail", user.getEmail());
            data.put("createdAt", FieldValue.serverTimestamp());

            DocumentReference docRef = firestore.collection(Constants.COL_EVENTS).add(data).get();

            // Re-read from Firestore to get resolved server timestamp
            DocumentSnapshot created = docRef.get().get();

            // Notify super admins of the department about new pending event
            try {
                List<QueryDocumentSnapshot> superAdmins = firestore.collection(Constants.COL_SUPER_ADMINS)
                        .whereEqualTo("department", user.getDepartment())
                        .get().get().getDocuments();
                for (QueryDocumentSnapshot sa : superAdmins) {
                    String saEmail = sa.getString("email");
                    if (saEmail != null) {
                        notificationService.notifyUser(saEmail,
                                "New Event Pending",
                                "\"" + request.getTitle() + "\" by " + user.getClubName() + " needs approval",
                                "reminder", "EventDetail", docRef.getId());
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to notify super admins", ex);
            }

            return toEventResponse(created);

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to create event");
        }
    }

    public EventResponse updateEvent(String eventId, EventRequest request, FirebaseUserDetails user) {
        if (!Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Only super admins can edit events");
        }

        try {
            DocumentReference docRef = firestore.collection(Constants.COL_EVENTS).document(eventId);
            DocumentSnapshot existing = docRef.get().get();
            if (!existing.exists()) {
                throw ApiException.notFound("Event not found");
            }

            Map<String, Object> updates = new HashMap<>();

            if (request.getTitle() != null) updates.put("title", request.getTitle());
            if (request.getDescription() != null) updates.put("description", request.getDescription());
            if (request.getChiefGuest() != null) updates.put("chiefGuest", request.getChiefGuest());
            if (request.getStartDate() != null) updates.put("startDate", request.getStartDate());
            if (request.getEndDate() != null) updates.put("endDate", request.getEndDate());
            if (request.getStartTime() != null) updates.put("startTime", request.getStartTime());
            if (request.getEndTime() != null) updates.put("endTime", request.getEndTime());
            if (request.getVenue() != null) updates.put("venue", request.getVenue());
            if (request.getTotalSeats() != null) updates.put("totalSeats", request.getTotalSeats());
            if (request.getPrice() != null) updates.put("price", request.getPrice());
            if (request.getPosterImage() != null) updates.put("posterImage", request.getPosterImage());
            if (request.getAudience() != null) updates.put("audience", request.getAudience());
            if (request.getPromoted() != null) updates.put("promoted", request.getPromoted());

            if (request.getStatus() != null) {
                updates.put("status", request.getStatus());
                if (Constants.STATUS_APPROVED.equals(request.getStatus())) {
                    updates.put("approvedBy", user.getEmail());
                    updates.put("approvedAt", FieldValue.serverTimestamp());
                } else if (Constants.STATUS_REJECTED.equals(request.getStatus())) {
                    updates.put("rejectedBy", user.getEmail());
                    updates.put("rejectedAt", FieldValue.serverTimestamp());
                }
            }

            docRef.update(updates).get();

            // Send notifications on status change
            if (request.getStatus() != null) {
                String eventTitle = existing.getString("title");
                String createdBy = existing.getString("createdByEmail");
                String eventDept = existing.getString("department");
                String audience = request.getAudience() != null ? request.getAudience()
                        : existing.getString("audience");

                if (Constants.STATUS_APPROVED.equals(request.getStatus())) {
                    // Notify club head
                    if (createdBy != null) {
                        notificationService.notifyUser(createdBy,
                                "Event Approved!",
                                "\"" + eventTitle + "\" has been approved and is now live",
                                "event", "EventDetail", eventId);
                    }
                    // Notify students
                    if (Constants.AUDIENCE_ALL.equals(audience)) {
                        notificationService.notifyAll(
                                "New Event: " + eventTitle,
                                "Check out this new event — book now!",
                                "event", "EventDetail", eventId);
                    } else if (eventDept != null) {
                        notificationService.notifyDepartment(eventDept,
                                "New Event for " + eventDept,
                                eventTitle + " — exclusive for your department!",
                                "event", "EventDetail", eventId);
                    }
                } else if (Constants.STATUS_REJECTED.equals(request.getStatus())) {
                    if (createdBy != null) {
                        notificationService.notifyUser(createdBy,
                                "Event Rejected",
                                "\"" + eventTitle + "\" was rejected by the admin",
                                "warning", null, null);
                    }
                }
            }

            // Return updated event
            DocumentSnapshot updated = docRef.get().get();
            return toEventResponse(updated);

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to update event");
        }
    }

    public List<EventResponse> getPendingEvents(String department) {
        try {
            Query query = firestore.collection(Constants.COL_EVENTS)
                    .whereEqualTo("status", Constants.STATUS_PENDING)
                    .orderBy("createdAt", Query.Direction.DESCENDING);

            List<QueryDocumentSnapshot> docs = query.get().get().getDocuments();
            List<EventResponse> events = new ArrayList<>();

            for (QueryDocumentSnapshot doc : docs) {
                // Super admin only sees events from their department
                String eventDept = doc.getString("department");
                if (department == null || department.equals(eventDept)) {
                    events.add(toEventResponse(doc));
                }
            }
            return events;

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch pending events");
        }
    }

    public List<EventResponse> getMyEvents(String createdByEmail) {
        try {
            Query query = firestore.collection(Constants.COL_EVENTS)
                    .whereEqualTo("createdByEmail", createdByEmail)
                    .orderBy("createdAt", Query.Direction.DESCENDING);

            return query.get().get().getDocuments().stream()
                    .map(this::toEventResponse)
                    .toList();

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch events");
        }
    }

    public void deleteEvent(String eventId, FirebaseUserDetails user) {
        if (!Constants.ROLE_CLUB_HEAD.equals(user.getRole())
                && !Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Not authorized to delete events");
        }

        try {
            DocumentReference docRef = firestore.collection(Constants.COL_EVENTS).document(eventId);
            DocumentSnapshot doc = docRef.get().get();
            if (!doc.exists()) {
                throw ApiException.notFound("Event not found");
            }

            String status = doc.getString("status");
            if (Constants.STATUS_APPROVED.equals(status)) {
                throw ApiException.badRequest("Cannot delete an approved event");
            }

            // Club heads can only delete their own events
            if (Constants.ROLE_CLUB_HEAD.equals(user.getRole())) {
                String createdBy = doc.getString("createdByEmail");
                if (!user.getEmail().equals(createdBy)) {
                    throw ApiException.forbidden("You can only delete your own events");
                }
            }

            docRef.delete().get();

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to delete event");
        }
    }

    public List<EventResponse> getEventsByClub(String clubId) {
        try {
            Query query = firestore.collection(Constants.COL_EVENTS)
                    .whereEqualTo("clubId", clubId)
                    .orderBy("createdAt", Query.Direction.DESCENDING);

            return query.get().get().getDocuments().stream()
                    .map(this::toEventResponse)
                    .toList();

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch club events");
        }
    }

    private EventResponse toEventResponse(DocumentSnapshot doc) {
        return toEventResponse(doc.getId(), doc.getData());
    }

    private EventResponse toEventResponse(String id, Map<String, Object> data) {
        return EventResponse.builder()
                .id(id)
                .title((String) data.get("title"))
                .description((String) data.get("description"))
                .chiefGuest((String) data.get("chiefGuest"))
                .startDate((String) data.get("startDate"))
                .endDate((String) data.get("endDate"))
                .startTime((String) data.get("startTime"))
                .endTime((String) data.get("endTime"))
                .venue((String) data.get("venue"))
                .totalSeats(toInt(data.get("totalSeats")))
                .bookedSeats(toInt(data.get("bookedSeats")))
                .price(toDouble(data.get("price")))
                .posterImage((String) data.get("posterImage"))
                .status((String) data.get("status"))
                .audience((String) data.get("audience"))
                .promoted(Boolean.TRUE.equals(data.get("promoted")))
                .clubId((String) data.get("clubId"))
                .clubName((String) data.get("clubName"))
                .clubUpiId((String) data.get("clubUpiId"))
                .department((String) data.get("department"))
                .createdByEmail((String) data.get("createdByEmail"))
                .approvedBy((String) data.get("approvedBy"))
                .approvedAt(data.get("approvedAt"))
                .createdAt(data.get("createdAt"))
                .interestedCount(toInt(data.get("interestedCount")))
                .build();
    }

    private Integer toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }

    private Double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0.0;
    }
}
