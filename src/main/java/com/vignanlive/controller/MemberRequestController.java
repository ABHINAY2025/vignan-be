package com.vignanlive.controller;

import com.vignanlive.dto.MemberRequestDto;
import com.vignanlive.exception.ApiException;
import com.vignanlive.security.FirebaseUserDetails;
import com.vignanlive.service.MemberRequestService;
import com.vignanlive.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/member-requests")
@RequiredArgsConstructor
public class MemberRequestController {

    private final MemberRequestService memberRequestService;

    @PostMapping
    public ResponseEntity<MemberRequestDto> createRequest(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @RequestBody MemberRequestDto request) {
        if (!Constants.ROLE_CLUB_HEAD.equals(user.getRole())) {
            throw ApiException.forbidden("Only club heads can create member requests");
        }
        return ResponseEntity.ok(memberRequestService.createRequest(request, user.getEmail()));
    }

    @GetMapping("/{clubId}")
    public ResponseEntity<List<MemberRequestDto>> listRequests(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String clubId) {
        if (!Constants.ROLE_CLUB_HEAD.equals(user.getRole())
                && !Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Not authorized");
        }
        return ResponseEntity.ok(memberRequestService.listRequests(clubId));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<MemberRequestDto> approveRequest(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String id) {
        if (!Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Only super admins can approve requests");
        }
        return ResponseEntity.ok(memberRequestService.approveRequest(id, user.getEmail()));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<MemberRequestDto> rejectRequest(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String id) {
        if (!Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Only super admins can reject requests");
        }
        return ResponseEntity.ok(memberRequestService.rejectRequest(id, user.getEmail()));
    }
}
