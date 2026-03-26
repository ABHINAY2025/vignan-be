package com.vignanlive.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.vignanlive.dto.SuperAdminRequest;
import com.vignanlive.dto.SuperAdminResponse;
import com.vignanlive.exception.ApiException;
import com.vignanlive.service.RoleCacheService;
import com.vignanlive.security.FirebaseUserDetails;
import com.vignanlive.util.Constants;
import com.vignanlive.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/super-admins")
@RequiredArgsConstructor
public class SuperAdminController {

    private final Firestore firestore;
    private final FirebaseAuth firebaseAuth;
    private final RoleCacheService roleCacheService;

    @GetMapping
    public ResponseEntity<List<SuperAdminResponse>> listSuperAdmins(
            @AuthenticationPrincipal FirebaseUserDetails user) {
        requireMaster(user);

        try {
            var docs = firestore.collection(Constants.COL_SUPER_ADMINS)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get().get().getDocuments();

            List<SuperAdminResponse> admins = docs.stream()
                    .map(doc -> SuperAdminResponse.builder()
                            .email(doc.getString("email"))
                            .department(doc.getString("department"))
                            .addedBy(doc.getString("addedBy"))
                            .createdAt(doc.get("createdAt"))
                            .build())
                    .toList();

            return ResponseEntity.ok(admins);

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch super admins");
        }
    }

    @PostMapping
    public ResponseEntity<SuperAdminResponse> createSuperAdmin(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @RequestBody SuperAdminRequest request) {
        requireMaster(user);

        String email = EmailUtils.normalize(request.getEmail());
        EmailUtils.validateEmail(email);

        // Create Firebase Auth account
        try {
            UserRecord.CreateRequest createReq = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(request.getPassword());
            firebaseAuth.createUser(createReq);
        } catch (FirebaseAuthException e) {
            if (!"EMAIL_ALREADY_EXISTS".equals(e.getAuthErrorCode().name())) {
                throw ApiException.badRequest("Failed to create account: " + e.getMessage());
            }
        }

        // Create Firestore document
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("email", email);
            data.put("department", request.getDepartment());
            data.put("addedBy", user.getEmail());
            data.put("createdAt", FieldValue.serverTimestamp());

            firestore.collection(Constants.COL_SUPER_ADMINS).document(email).set(data).get();
            roleCacheService.invalidate(email);

            return ResponseEntity.ok(SuperAdminResponse.builder()
                    .email(email)
                    .department(request.getDepartment())
                    .addedBy(user.getEmail())
                    .build());

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to create super admin");
        }
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<Void> removeSuperAdmin(
            @AuthenticationPrincipal FirebaseUserDetails user,
            @PathVariable String email) {
        requireMaster(user);

        String normalizedEmail = EmailUtils.normalize(email);
        try {
            DocumentSnapshot doc = firestore.collection(Constants.COL_SUPER_ADMINS)
                    .document(normalizedEmail).get().get();
            if (!doc.exists()) {
                throw ApiException.notFound("Super admin not found");
            }

            firestore.collection(Constants.COL_SUPER_ADMINS).document(normalizedEmail).delete().get();
            roleCacheService.invalidate(normalizedEmail);

            return ResponseEntity.noContent().build();

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to remove super admin");
        }
    }

    private void requireMaster(FirebaseUserDetails user) {
        if (!Constants.ROLE_MASTER_ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Only master admins can manage super admins");
        }
    }
}
