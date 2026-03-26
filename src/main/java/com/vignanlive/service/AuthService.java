package com.vignanlive.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.vignanlive.dto.AuthResponse;
import com.vignanlive.dto.CreateAccountRequest;
import com.vignanlive.exception.ApiException;
import com.vignanlive.security.FirebaseUserDetails;
import com.vignanlive.util.Constants;
import com.vignanlive.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final Firestore firestore;
    private final FirebaseAuth firebaseAuth;
    private final RoleCacheService roleCacheService;

    public record RoleResult(String role, Map<String, Object> profile) {}

    /**
     * Detect user role by checking Firestore collections in priority order.
     * Mirrors AuthContext.jsx detectRole().
     */
    public RoleResult detectRole(String email) {
        String normalizedEmail = EmailUtils.normalize(email);

        try {
            // 1. Check master_admins
            DocumentSnapshot doc = firestore.collection(Constants.COL_MASTER_ADMINS)
                    .document(normalizedEmail).get().get();
            if (doc.exists()) {
                return new RoleResult(Constants.ROLE_MASTER_ADMIN, doc.getData());
            }

            // 2. Check super_admins
            doc = firestore.collection(Constants.COL_SUPER_ADMINS)
                    .document(normalizedEmail).get().get();
            if (doc.exists()) {
                return new RoleResult(Constants.ROLE_SUPER_ADMIN, doc.getData());
            }

            // 3. Check club_heads
            doc = firestore.collection(Constants.COL_CLUB_HEADS)
                    .document(normalizedEmail).get().get();
            if (doc.exists()) {
                return new RoleResult(Constants.ROLE_CLUB_HEAD, doc.getData());
            }

            // 4. Check club_members
            doc = firestore.collection(Constants.COL_CLUB_MEMBERS)
                    .document(normalizedEmail).get().get();
            if (doc.exists()) {
                return new RoleResult(Constants.ROLE_CLUB_MEMBER, doc.getData());
            }

            // 5. Check users (students)
            doc = firestore.collection(Constants.COL_USERS)
                    .document(normalizedEmail).get().get();
            if (doc.exists()) {
                return new RoleResult(Constants.ROLE_STUDENT, doc.getData());
            }

            return new RoleResult(null, null);

        } catch (InterruptedException | ExecutionException e) {
            log.error("Role detection failed for {}", normalizedEmail, e);
            throw ApiException.internal("Failed to detect user role");
        }
    }

    /**
     * Get profile data for the authenticated user.
     */
    public AuthResponse getProfile(FirebaseUserDetails user) {
        return AuthResponse.builder()
                .email(user.getEmail())
                .role(user.getRole())
                .department(user.getDepartment())
                .clubId(user.getClubId())
                .clubName(user.getClubName())
                .upiId(user.getUpiId())
                .permissions(user.getPermissions())
                .rollNumber(EmailUtils.getRollNumber(user.getEmail()))
                .build();
    }

    /**
     * Register a new student by creating a users/{email} doc.
     * Called after Firebase Auth signup from the frontend.
     */
    public AuthResponse registerStudent(String email, String department) {
        String normalizedEmail = EmailUtils.normalize(email);

        // Check if already registered in any role collection
        RoleResult existing = detectRole(normalizedEmail);
        if (existing.role() != null) {
            // Already pre-registered as admin/head/member — just return profile
            return AuthResponse.builder()
                    .email(normalizedEmail)
                    .role(existing.role())
                    .department((String) existing.profile().get("department"))
                    .rollNumber(EmailUtils.getRollNumber(normalizedEmail))
                    .build();
        }

        // Create student doc
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("email", normalizedEmail);
            data.put("rollNumber", EmailUtils.getRollNumber(normalizedEmail));
            data.put("department", department);
            data.put("createdAt", FieldValue.serverTimestamp());

            firestore.collection(Constants.COL_USERS).document(normalizedEmail).set(data).get();
            roleCacheService.invalidate(normalizedEmail);

            return AuthResponse.builder()
                    .email(normalizedEmail)
                    .role(Constants.ROLE_STUDENT)
                    .department(department)
                    .rollNumber(EmailUtils.getRollNumber(normalizedEmail))
                    .build();

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to register student {}", normalizedEmail, e);
            throw ApiException.internal("Failed to register student");
        }
    }

    /**
     * Create a Firebase Auth account and corresponding role document.
     * Replaces the frontend's createAccountWithoutSignIn().
     */
    public Map<String, Object> createAccount(CreateAccountRequest request, String creatorEmail) {
        String email = EmailUtils.normalize(request.getEmail());
        EmailUtils.validateEmail(email);

        // Create Firebase Auth account
        try {
            UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(request.getPassword());
            firebaseAuth.createUser(createRequest);
        } catch (FirebaseAuthException e) {
            if ("EMAIL_ALREADY_EXISTS".equals(e.getAuthErrorCode().name())) {
                log.info("Firebase account already exists for {}, proceeding to create role doc", email);
            } else {
                throw ApiException.badRequest("Failed to create account: " + e.getMessage());
            }
        }

        // Create the role-specific Firestore document
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("email", email);
            data.put("addedBy", creatorEmail);
            data.put("createdAt", FieldValue.serverTimestamp());

            switch (request.getRole()) {
                case Constants.ROLE_SUPER_ADMIN -> {
                    data.put("department", request.getDepartment());
                    firestore.collection(Constants.COL_SUPER_ADMINS).document(email).set(data).get();
                }
                case Constants.ROLE_CLUB_HEAD -> {
                    data.put("clubId", request.getClubId());
                    data.put("clubName", request.getClubName());
                    data.put("upiId", request.getUpiId());
                    data.put("department", request.getDepartment());
                    firestore.collection(Constants.COL_CLUB_HEADS).document(email).set(data).get();
                }
                case Constants.ROLE_CLUB_MEMBER -> {
                    data.put("clubId", request.getClubId());
                    data.put("clubName", request.getClubName());
                    data.put("department", request.getDepartment());
                    data.put("permissions", request.getPermissions());
                    firestore.collection(Constants.COL_CLUB_MEMBERS).document(email).set(data).get();
                }
                default -> throw ApiException.badRequest("Invalid role: " + request.getRole());
            }

            // Invalidate role cache for this email
            roleCacheService.invalidate(email);

            return data;

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create role document for {}", email, e);
            throw ApiException.internal("Failed to create role document");
        }
    }
}
