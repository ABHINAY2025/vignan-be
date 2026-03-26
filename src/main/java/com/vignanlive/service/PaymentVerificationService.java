package com.vignanlive.service;

import com.google.cloud.firestore.*;
import com.vignanlive.dto.BulkVerifyResponse;
import com.vignanlive.dto.BulkVerifyResponse.VerifyDetail;
import com.vignanlive.exception.ApiException;
import com.vignanlive.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentVerificationService {

    private final Firestore firestore;

    /**
     * Parse bank statement file, match UTRs against pending bookings, bulk verify.
     * Replicates the auto-match logic from ClubEventDashboard.jsx.
     */
    public BulkVerifyResponse bulkVerify(String eventId, MultipartFile file, String verifiedBy) {
        // 1. Extract text from file
        String fileText = extractText(file);
        String fileTextUpper = fileText.toUpperCase();

        // 2. Get the event to check price
        double eventPrice;
        try {
            DocumentSnapshot eventDoc = firestore.collection(Constants.COL_EVENTS)
                    .document(eventId).get().get();
            if (!eventDoc.exists()) {
                throw ApiException.notFound("Event not found");
            }
            Object priceObj = eventDoc.get("price");
            eventPrice = priceObj instanceof Number ? ((Number) priceObj).doubleValue() : 0;
        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch event");
        }

        // 3. Query pending bookings for this event
        List<QueryDocumentSnapshot> pendingBookings;
        try {
            pendingBookings = firestore.collection(Constants.COL_BOOKINGS)
                    .whereEqualTo("eventId", eventId)
                    .whereEqualTo("paymentStatus", Constants.PAY_PENDING)
                    .get().get().getDocuments();
        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch pending bookings");
        }

        // 4. Match UTRs
        List<VerifyDetail> details = new ArrayList<>();
        List<String> matchedBookingIds = new ArrayList<>();
        int matched = 0, amountMismatch = 0, unmatched = 0;

        for (QueryDocumentSnapshot booking : pendingBookings) {
            String utr = booking.getString("transactionId");
            String bookingId = booking.getId();

            if (utr == null || utr.isBlank()) {
                details.add(VerifyDetail.builder()
                        .bookingId(bookingId)
                        .transactionId("")
                        .status("no_utr")
                        .message("No UTR entered")
                        .build());
                unmatched++;
                continue;
            }

            if (fileTextUpper.contains(utr.toUpperCase())) {
                // Check if this UTR is already used by another non-rejected booking
                boolean utrDuplicate = false;
                try {
                    List<QueryDocumentSnapshot> utrDups = firestore.collection(Constants.COL_BOOKINGS)
                            .whereEqualTo("transactionId", utr)
                            .get().get().getDocuments();
                    utrDuplicate = utrDups.stream().anyMatch(doc ->
                            !doc.getId().equals(bookingId) && !Constants.PAY_REJECTED.equals(doc.getString("paymentStatus")));
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Failed to check UTR uniqueness for {}", utr, e);
                }

                if (utrDuplicate) {
                    details.add(VerifyDetail.builder()
                            .bookingId(bookingId)
                            .transactionId(utr)
                            .status("duplicate_utr")
                            .message("UTR already used by another booking")
                            .build());
                    unmatched++;
                    continue;
                }

                // UTR found in file, check amount
                Object amountObj = booking.get("paidAmount");
                double paidAmount = amountObj instanceof Number ? ((Number) amountObj).doubleValue() : 0;

                if (eventPrice > 0 && Math.abs(paidAmount - eventPrice) > 0.01) {
                    details.add(VerifyDetail.builder()
                            .bookingId(bookingId)
                            .transactionId(utr)
                            .status("amount_mismatch")
                            .message("Paid " + paidAmount + " but event price is " + eventPrice)
                            .build());
                    amountMismatch++;
                } else {
                    matchedBookingIds.add(bookingId);
                    details.add(VerifyDetail.builder()
                            .bookingId(bookingId)
                            .transactionId(utr)
                            .status("matched")
                            .message("UTR found and amount verified")
                            .build());
                    matched++;
                }
            } else {
                details.add(VerifyDetail.builder()
                        .bookingId(bookingId)
                        .transactionId(utr)
                        .status("not_found")
                        .message("UTR not found in bank statement")
                        .build());
                unmatched++;
            }
        }

        // 5. Bulk update matched bookings (seats already counted at booking time)
        int verified = 0;
        for (String bookingId : matchedBookingIds) {
            try {
                firestore.collection(Constants.COL_BOOKINGS).document(bookingId).update(
                        "paymentStatus", Constants.PAY_CONFIRMED,
                        "verifiedAt", FieldValue.serverTimestamp(),
                        "verifiedBy", "auto-match"
                ).get();
                verified++;
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to verify booking {}", bookingId, e);
            }
        }

        return BulkVerifyResponse.builder()
                .matched(matched)
                .amountMismatch(amountMismatch)
                .unmatched(unmatched)
                .verified(verified)
                .details(details)
                .build();
    }

    private String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) filename = "";

        try {
            if (filename.toLowerCase().endsWith(".pdf")) {
                try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(document);
                }
            } else {
                // CSV, TXT, or other text formats
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Failed to read file: " + e.getMessage());
        }
    }
}
