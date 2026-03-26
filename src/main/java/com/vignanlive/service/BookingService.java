package com.vignanlive.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.vignanlive.dto.BookingRequest;
import com.vignanlive.dto.BookingResponse;
import com.vignanlive.exception.ApiException;
import com.vignanlive.security.FirebaseUserDetails;
import com.vignanlive.util.Constants;
import com.vignanlive.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final Firestore firestore;
    private final NotificationService notificationService;

    private final ConcurrentHashMap<String, List<Long>> bookingAttempts = new ConcurrentHashMap<>();
    private static final int MAX_BOOKINGS_PER_MINUTE = 5;

    /**
     * Create a booking using a Firestore transaction for atomicity.
     * Prevents duplicate bookings and overselling.
     */
    public BookingResponse createBooking(BookingRequest request, FirebaseUserDetails user) {
        String email = user.getEmail();
        String eventId = request.getEventId();

        // Rate limiting
        List<Long> attempts = bookingAttempts.computeIfAbsent(email, k -> new ArrayList<>());
        long now = System.currentTimeMillis();
        synchronized (attempts) {
            attempts.removeIf(ts -> now - ts > 60_000);
            if (attempts.size() >= MAX_BOOKINGS_PER_MINUTE) {
                throw ApiException.tooManyRequests("Too many booking attempts. Please wait a minute.");
            }
            attempts.add(now);
        }

        try {
            ApiFuture<BookingResponse> result = firestore.runTransaction(transaction -> {
                // 1. Read event
                DocumentReference eventRef = firestore.collection(Constants.COL_EVENTS).document(eventId);
                DocumentSnapshot eventDoc = transaction.get(eventRef).get();

                if (!eventDoc.exists()) {
                    throw ApiException.notFound("Event not found");
                }

                String status = eventDoc.getString("status");
                if (!Constants.STATUS_APPROVED.equals(status)) {
                    throw ApiException.badRequest("Event is not available for booking");
                }

                int totalSeats = toInt(eventDoc.get("totalSeats"));
                int bookedSeats = toInt(eventDoc.get("bookedSeats"));
                double price = toDouble(eventDoc.get("price"));

                if (bookedSeats >= totalSeats) {
                    throw ApiException.conflict("Event is sold out");
                }

                // 2. Check for duplicate booking (exclude rejected bookings)
                List<QueryDocumentSnapshot> existing = firestore.collection(Constants.COL_BOOKINGS)
                        .whereEqualTo("userEmail", email)
                        .whereEqualTo("eventId", eventId)
                        .get().get().getDocuments();

                boolean hasActiveBooking = existing.stream().anyMatch(doc -> {
                    String ps = doc.getString("paymentStatus");
                    return !Constants.PAY_REJECTED.equals(ps);
                });

                if (hasActiveBooking) {
                    throw ApiException.conflict("You have already booked this event");
                }

                // 3. Determine payment status
                String paymentStatus;
                String transactionId = request.getTransactionId();
                double paidAmount;

                if (price <= 0) {
                    paymentStatus = Constants.PAY_CONFIRMED;
                    transactionId = "";
                    paidAmount = 0;
                } else {
                    if (transactionId == null || transactionId.isBlank()) {
                        throw ApiException.badRequest("UTR number is required for paid events");
                    }

                    if (!transactionId.matches("^\\d{12}$")) {
                        throw ApiException.badRequest("Invalid UTR format — must be exactly 12 digits");
                    }

                    // 3a. Check UTR uniqueness globally — no two non-rejected bookings can share a UTR
                    List<QueryDocumentSnapshot> utrDuplicates = firestore.collection(Constants.COL_BOOKINGS)
                            .whereEqualTo("transactionId", transactionId)
                            .get().get().getDocuments();

                    boolean utrAlreadyUsed = utrDuplicates.stream().anyMatch(doc -> {
                        String ps = doc.getString("paymentStatus");
                        return !Constants.PAY_REJECTED.equals(ps);
                    });

                    if (utrAlreadyUsed) {
                        throw ApiException.conflict("This UTR number has already been used for another booking");
                    }

                    paymentStatus = Constants.PAY_PENDING;
                    paidAmount = price;
                }

                // 4. Create booking document
                Map<String, Object> bookingData = new HashMap<>();
                bookingData.put("userEmail", email);
                bookingData.put("rollNumber", EmailUtils.getRollNumber(email));
                bookingData.put("department", user.getDepartment());
                bookingData.put("eventId", eventId);
                bookingData.put("eventTitle", eventDoc.getString("title"));
                bookingData.put("clubName", eventDoc.getString("clubName"));
                bookingData.put("clubUpiId", eventDoc.getString("clubUpiId"));
                bookingData.put("paidAmount", paidAmount);
                bookingData.put("transactionId", transactionId);
                bookingData.put("paymentStatus", paymentStatus);
                bookingData.put("attended", false);
                bookingData.put("createdAt", FieldValue.serverTimestamp());

                DocumentReference bookingRef = firestore.collection(Constants.COL_BOOKINGS).document();
                transaction.set(bookingRef, bookingData);

                // 5. Increment booked seats immediately (decremented if rejected later)
                transaction.update(eventRef, "bookedSeats", FieldValue.increment(1));

                return BookingResponse.builder()
                        .id(bookingRef.getId())
                        .userEmail(email)
                        .rollNumber(EmailUtils.getRollNumber(email))
                        .department(user.getDepartment())
                        .eventId(eventId)
                        .eventTitle(eventDoc.getString("title"))
                        .clubName(eventDoc.getString("clubName"))
                        .clubUpiId(eventDoc.getString("clubUpiId"))
                        .paidAmount(paidAmount)
                        .transactionId(transactionId)
                        .paymentStatus(paymentStatus)
                        .attended(false)
                        .build();
            });

            BookingResponse booking = result.get();

            // Notify student on free event instant confirmation
            if (Constants.PAY_CONFIRMED.equals(booking.getPaymentStatus())) {
                notificationService.notifyUser(booking.getUserEmail(),
                        "Booking Confirmed!",
                        "You're in for " + booking.getEventTitle() + " — show your QR at entry",
                        "booking", "BookingTicket", booking.getId());
            }

            // Notify club head of new booking
            try {
                DocumentSnapshot ev = firestore.collection(Constants.COL_EVENTS).document(booking.getEventId()).get().get();
                String headEmail = ev.getString("createdByEmail");
                if (headEmail != null) {
                    notificationService.notifyUser(headEmail,
                            "New Booking",
                            booking.getRollNumber() + " booked " + booking.getEventTitle(),
                            "reminder", "ClubEventDashboard", booking.getEventId());
                }
            } catch (Exception ex) {
                log.error("Failed to notify club head", ex);
            }

            return booking;

        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ApiException) {
                throw (ApiException) cause;
            }
            throw ApiException.internal("Failed to create booking");
        }
    }

    public List<BookingResponse> getMyBookings(String userEmail) {
        try {
            Query query = firestore.collection(Constants.COL_BOOKINGS)
                    .whereEqualTo("userEmail", userEmail)
                    .orderBy("createdAt", Query.Direction.DESCENDING);

            return query.get().get().getDocuments().stream()
                    .map(this::toBookingResponse)
                    .toList();

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch bookings");
        }
    }

    public BookingResponse getBooking(String bookingId) {
        try {
            DocumentSnapshot doc = firestore.collection(Constants.COL_BOOKINGS)
                    .document(bookingId).get().get();
            if (!doc.exists()) {
                throw ApiException.notFound("Booking not found");
            }
            return toBookingResponse(doc);
        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch booking");
        }
    }

    public List<BookingResponse> getEventBookings(String eventId) {
        try {
            List<QueryDocumentSnapshot> docs = firestore.collection(Constants.COL_BOOKINGS)
                    .whereEqualTo("eventId", eventId)
                    .get().get().getDocuments();

            // Sort client-side by createdAt desc (same pattern as frontend)
            return docs.stream()
                    .map(this::toBookingResponse)
                    .toList();

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch event bookings");
        }
    }

    public BookingResponse verifyPayment(String bookingId, String verifiedBy) {
        try {
            DocumentReference docRef = firestore.collection(Constants.COL_BOOKINGS).document(bookingId);
            DocumentSnapshot doc = docRef.get().get();
            if (!doc.exists()) {
                throw ApiException.notFound("Booking not found");
            }

            // Check if this UTR is already verified for another booking
            String utr = doc.getString("transactionId");
            if (utr != null && !utr.isBlank()) {
                List<QueryDocumentSnapshot> utrDups = firestore.collection(Constants.COL_BOOKINGS)
                        .whereEqualTo("transactionId", utr)
                        .get().get().getDocuments();

                boolean utrAlreadyVerified = utrDups.stream().anyMatch(d ->
                        !d.getId().equals(bookingId) && Constants.PAY_CONFIRMED.equals(d.getString("paymentStatus")));

                if (utrAlreadyVerified) {
                    throw ApiException.conflict("This UTR is already verified for another booking");
                }
            }

            docRef.update(
                    "paymentStatus", Constants.PAY_CONFIRMED,
                    "verifiedAt", FieldValue.serverTimestamp(),
                    "verifiedBy", verifiedBy
            ).get();

            // Notify student: payment verified
            String userEmail = doc.getString("userEmail");
            String eventTitle = doc.getString("eventTitle");
            if (userEmail != null) {
                notificationService.notifyUser(userEmail,
                        "Payment Verified!",
                        "Your booking for " + (eventTitle != null ? eventTitle : "the event") + " is confirmed",
                        "booking", "BookingTicket", bookingId);
            }

            DocumentSnapshot updated = docRef.get().get();
            return toBookingResponse(updated);

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to verify payment");
        }
    }

    public BookingResponse rejectPayment(String bookingId, String rejectedBy) {
        try {
            DocumentReference docRef = firestore.collection(Constants.COL_BOOKINGS).document(bookingId);
            DocumentSnapshot doc = docRef.get().get();
            if (!doc.exists()) {
                throw ApiException.notFound("Booking not found");
            }

            String currentStatus = doc.getString("paymentStatus");
            if (Constants.PAY_REJECTED.equals(currentStatus)) {
                throw ApiException.conflict("Booking is already rejected");
            }

            docRef.update(
                    "paymentStatus", Constants.PAY_REJECTED,
                    "rejectedAt", FieldValue.serverTimestamp(),
                    "rejectedBy", rejectedBy
            ).get();

            // Decrement seat count — seat was reserved at booking time
            String eventId = doc.getString("eventId");
            DocumentReference eventRef = firestore.collection(Constants.COL_EVENTS).document(eventId);
            eventRef.update("bookedSeats", FieldValue.increment(-1)).get();

            // Notify student: payment rejected
            String userEmail = doc.getString("userEmail");
            String eventTitle = doc.getString("eventTitle");
            if (userEmail != null) {
                notificationService.notifyUser(userEmail,
                        "Payment Rejected",
                        "Your payment for " + (eventTitle != null ? eventTitle : "the event") + " was rejected. Contact the club head.",
                        "warning", "MyBookings", null);
            }

            DocumentSnapshot updated = docRef.get().get();
            return toBookingResponse(updated);

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to reject payment");
        }
    }

    public BookingResponse markAttendance(String bookingId) {
        try {
            DocumentReference docRef = firestore.collection(Constants.COL_BOOKINGS).document(bookingId);
            DocumentSnapshot doc = docRef.get().get();
            if (!doc.exists()) {
                throw ApiException.notFound("Booking not found");
            }

            String paymentStatus = doc.getString("paymentStatus");
            if (!Constants.PAY_CONFIRMED.equals(paymentStatus)) {
                throw ApiException.badRequest("Payment not confirmed for this booking");
            }

            Boolean attended = doc.getBoolean("attended");
            if (Boolean.TRUE.equals(attended)) {
                throw ApiException.conflict("Already checked in");
            }

            docRef.update(
                    "attended", true,
                    "attendedAt", FieldValue.serverTimestamp()
            ).get();

            DocumentSnapshot updated = docRef.get().get();
            return toBookingResponse(updated);

        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof ApiException) throw (ApiException) e.getCause();
            throw ApiException.internal("Failed to mark attendance");
        }
    }

    public List<BookingResponse> getAdminBookings(String department) {
        try {
            // Get all events for this department
            List<QueryDocumentSnapshot> events = firestore.collection(Constants.COL_EVENTS)
                    .whereEqualTo("department", department)
                    .get().get().getDocuments();

            List<String> eventIds = events.stream()
                    .map(DocumentSnapshot::getId)
                    .toList();

            if (eventIds.isEmpty()) {
                return List.of();
            }

            // Firestore "in" queries limited to 30 items
            List<BookingResponse> allBookings = new ArrayList<>();
            for (int i = 0; i < eventIds.size(); i += 30) {
                List<String> batch = eventIds.subList(i, Math.min(i + 30, eventIds.size()));
                List<QueryDocumentSnapshot> bookings = firestore.collection(Constants.COL_BOOKINGS)
                        .whereIn("eventId", batch)
                        .get().get().getDocuments();

                bookings.stream()
                        .map(this::toBookingResponse)
                        .forEach(allBookings::add);
            }

            return allBookings;

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch admin bookings");
        }
    }

    private BookingResponse toBookingResponse(DocumentSnapshot doc) {
        return BookingResponse.builder()
                .id(doc.getId())
                .userEmail(doc.getString("userEmail"))
                .rollNumber(doc.getString("rollNumber"))
                .department(doc.getString("department"))
                .eventId(doc.getString("eventId"))
                .eventTitle(doc.getString("eventTitle"))
                .clubName(doc.getString("clubName"))
                .clubUpiId(doc.getString("clubUpiId"))
                .paidAmount(toDouble(doc.get("paidAmount")))
                .transactionId(doc.getString("transactionId"))
                .paymentStatus(doc.getString("paymentStatus"))
                .attended(doc.getBoolean("attended"))
                .attendedAt(doc.get("attendedAt"))
                .verifiedAt(doc.get("verifiedAt"))
                .verifiedBy(doc.getString("verifiedBy"))
                .createdAt(doc.get("createdAt"))
                .build();
    }

    private int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }

    private double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0;
    }
}
