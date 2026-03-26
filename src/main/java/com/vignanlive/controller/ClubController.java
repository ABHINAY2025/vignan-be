package com.vignanlive.controller;

import com.vignanlive.dto.ChangeHeadRequest;
import com.vignanlive.dto.ClubRequest;
import com.vignanlive.dto.ClubResponse;
import com.vignanlive.exception.ApiException;
import com.vignanlive.security.FirebaseUserDetails;
import com.vignanlive.service.ClubService;
import com.vignanlive.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;

    @GetMapping
    public ResponseEntity<List<ClubResponse>> listClubs(
            @AuthenticationPrincipal FirebaseUserDetails user) {
        if (!Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Not authorized");
        }
        // Super admin sees clubs in their dept, master admin sees all
        String dept = Constants.ROLE_SUPER_ADMIN.equals(user.getRole()) ? user.getDepartment() : null;
        return ResponseEntity.ok(clubService.listClubs(dept));
    }

    @GetMapping("/all")
    public ResponseEntity<List<ClubResponse>> listAllClubs(
            @AuthenticationPrincipal FirebaseUserDetails user) {
        if (!Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Only master admins can view all clubs");
        }
        return ResponseEntity.ok(clubService.listAllClubs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClubResponse> getClub(@PathVariable String id) {
        return ResponseEntity.ok(clubService.getClub(id));
    }

    @PostMapping
    public ResponseEntity<ClubResponse> createClub(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @RequestBody ClubRequest request) {
        if (!Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Only super admins can create clubs");
        }
        return ResponseEntity.ok(clubService.createClub(request, user.getEmail()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClubResponse> updateClub(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String id,
            @RequestBody ClubRequest request) {
        if (!Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Only super admins can edit clubs");
        }
        return ResponseEntity.ok(clubService.updateClub(id, request));
    }

    @PutMapping("/{id}/head")
    public ResponseEntity<ClubResponse> changeHead(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String id,
            @RequestBody ChangeHeadRequest request) {
        if (!Constants.ROLE_SUPER_ADMIN.equals(user.getRole())
                && !Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Only super admins can change club heads");
        }
        return ResponseEntity.ok(clubService.changeHead(id, request, user.getEmail()));
    }
}
