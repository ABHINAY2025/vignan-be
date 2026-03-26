package com.vignanlive.controller;

import com.vignanlive.dto.ClubMemberRequest;
import com.vignanlive.dto.ClubMemberResponse;
import com.vignanlive.dto.UpdatePermissionsRequest;
import com.vignanlive.exception.ApiException;
import com.vignanlive.security.FirebaseUserDetails;
import com.vignanlive.service.ClubMemberService;
import com.vignanlive.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/club-members")
@RequiredArgsConstructor
public class ClubMemberController {

    private final ClubMemberService clubMemberService;

    @GetMapping("/{clubId}")
    public ResponseEntity<List<ClubMemberResponse>> listMembers(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String clubId) {
        if (!Constants.ROLE_CLUB_HEAD.equals(user.getRole())
                && !Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Not authorized to view members");
        }
        return ResponseEntity.ok(clubMemberService.listMembers(clubId));
    }

    @PostMapping
    public ResponseEntity<ClubMemberResponse> addMember(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @RequestBody ClubMemberRequest request) {
        if (!Constants.ROLE_CLUB_HEAD.equals(user.getRole())
                && !Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Not authorized to add members");
        }
        return ResponseEntity.ok(clubMemberService.addMember(request, user.getEmail()));
    }

    @PutMapping("/{email}/permissions")
    public ResponseEntity<ClubMemberResponse> updatePermissions(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String email,
            @RequestBody UpdatePermissionsRequest request) {
        if (!Constants.ROLE_CLUB_HEAD.equals(user.getRole())
                && !Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Not authorized to update permissions");
        }
        return ResponseEntity.ok(clubMemberService.updatePermissions(email, request.getPermissions()));
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String email) {
        if (!Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Only super admins can directly remove members");
        }
        clubMemberService.removeMember(email);
        return ResponseEntity.noContent().build();
    }
}
