package com.vignanlive.controller;

import com.vignanlive.dto.BookingRequest;
import com.vignanlive.dto.BookingResponse;
import com.vignanlive.dto.BulkVerifyResponse;
import com.vignanlive.exception.ApiException;
import com.vignanlive.security.FirebaseUserDetails;
import com.vignanlive.service.BookingService;
import com.vignanlive.service.PaymentVerificationService;
import com.vignanlive.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final PaymentVerificationService paymentVerificationService;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @RequestBody BookingRequest request) {
        return ResponseEntity.ok(bookingService.createBooking(request, user));
    }

    @GetMapping("/my")
    public ResponseEntity<List<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal FirebaseUserDetails user) {
        return ResponseEntity.ok(bookingService.getMyBookings(user.getEmail()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable String id) {
        return ResponseEntity.ok(bookingService.getBooking(id));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<BookingResponse>> getEventBookings(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String eventId) {
        requirePaymentAccess(user);
        return ResponseEntity.ok(bookingService.getEventBookings(eventId));
    }

    @PutMapping("/{id}/verify")
    public ResponseEntity<BookingResponse> verifyPayment(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String id) {
        requirePaymentAccess(user);
        return ResponseEntity.ok(bookingService.verifyPayment(id, user.getEmail()));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<BookingResponse> rejectPayment(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String id) {
        requirePaymentAccess(user);
        return ResponseEntity.ok(bookingService.rejectPayment(id, user.getEmail()));
    }

    @PostMapping("/event/{eventId}/bulk-verify")
    public ResponseEntity<BulkVerifyResponse> bulkVerify(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String eventId,
            @RequestParam("file") MultipartFile file) {
        requirePaymentAccess(user);
        return ResponseEntity.ok(paymentVerificationService.bulkVerify(eventId, file, user.getEmail()));
    }

    @PutMapping("/{id}/attend")
    public ResponseEntity<BookingResponse> markAttendance(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String id) {
        // Require club_member with manage_attendance or club_head or super_admin
        if (Constants.ROLE_CLUB_MEMBER.equals(user.getRole())) {
            if (!user.hasPermission(Constants.PERM_MANAGE_ATTENDANCE)) {
                throw ApiException.forbidden("Missing manage_attendance permission");
            }
        } else if (!Constants.ROLE_CLUB_HEAD.equals(user.getRole())
                && !Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Not authorized to manage attendance");
        }
        return ResponseEntity.ok(bookingService.markAttendance(id));
    }

    @GetMapping("/admin")
    public ResponseEntity<List<BookingResponse>> getAdminBookings(
            @AuthenticationPrincipal FirebaseUserDetails user) {
        if (!Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Only super admins can view all bookings");
        }
        return ResponseEntity.ok(bookingService.getAdminBookings(user.getDepartment()));
    }

    private void requirePaymentAccess(FirebaseUserDetails user) {
        if (Constants.ROLE_CLUB_MEMBER.equals(user.getRole())) {
            if (!user.hasPermission(Constants.PERM_VERIFY_PAYMENTS)) {
                throw ApiException.forbidden("Missing verify_payments permission");
            }
        } else if (!Constants.ROLE_CLUB_HEAD.equals(user.getRole())
                && !Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Not authorized to verify payments");
        }
    }
}
