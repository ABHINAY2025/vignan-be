package com.vignanlive.controller;

import com.vignanlive.dto.AuthResponse;
import com.vignanlive.dto.CreateAccountRequest;
import com.vignanlive.exception.ApiException;
import com.vignanlive.security.FirebaseUserDetails;
import com.vignanlive.service.AuthService;
import com.vignanlive.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal FirebaseUserDetails user) {
        return ResponseEntity.ok(authService.getProfile(user));
    }

    @PostMapping("/register-student")
    public ResponseEntity<AuthResponse> registerStudent(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @RequestBody Map<String, String> body) {
        String department = body.get("department");
        return ResponseEntity.ok(authService.registerStudent(user.getEmail(), department));
    }

    @PostMapping("/create-account")
    public ResponseEntity<Map<String, Object>> createAccount(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @RequestBody CreateAccountRequest request) {

        // Validate caller has sufficient role to create the requested role
        String callerRole = user.getRole();
        String targetRole = request.getRole();

        switch (targetRole) {
            case Constants.ROLE_SUPER_ADMIN -> {
                if (!Constants.ROLE_MASTER_ADMIN.equals(callerRole)) {
                    throw ApiException.forbidden("Only master admins can create super admins");
                }
            }
            case Constants.ROLE_CLUB_HEAD -> {
                if (!Constants.ROLE_SUPER_ADMIN.equals(callerRole)
                        && !Constants.ROLE_MASTER_ADMIN.equals(callerRole)) {
                    throw ApiException.forbidden("Only super admins can create club heads");
                }
            }
            case Constants.ROLE_CLUB_MEMBER -> {
                if (!Constants.ROLE_CLUB_HEAD.equals(callerRole)
                        && !Constants.ROLE_SUPER_ADMIN.equals(callerRole)
                        && !Constants.ROLE_MASTER_ADMIN.equals(callerRole)) {
                    throw ApiException.forbidden("Only club heads can add club members");
                }
            }
            default -> throw ApiException.badRequest("Invalid role: " + targetRole);
        }

        Map<String, Object> result = authService.createAccount(request, user.getEmail());
        return ResponseEntity.ok(result);
    }
}
