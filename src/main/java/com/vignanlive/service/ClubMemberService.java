package com.vignanlive.service;

import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.vignanlive.dto.ClubMemberRequest;
import com.vignanlive.dto.ClubMemberResponse;
import com.vignanlive.exception.ApiException;
import com.vignanlive.service.RoleCacheService;
import com.vignanlive.util.Constants;
import com.vignanlive.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClubMemberService {

    private final Firestore firestore;
    private final FirebaseAuth firebaseAuth;
    private final RoleCacheService roleCacheService;

    public List<ClubMemberResponse> listMembers(String clubId) {
        try {
            List<QueryDocumentSnapshot> docs = firestore.collection(Constants.COL_CLUB_MEMBERS)
                    .whereEqualTo("clubId", clubId)
                    .get().get().getDocuments();

            return docs.stream().map(this::toResponse).toList();

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch club members");
        }
    }

    public ClubMemberResponse addMember(ClubMemberRequest request, String addedBy) {
        String email = EmailUtils.normalize(request.getEmail());
        EmailUtils.validateEmail(email);

        // Create Firebase Auth account (ignore if exists)
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

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("email", email);
            data.put("clubId", request.getClubId());
            data.put("clubName", request.getClubName());
            data.put("department", request.getDepartment());
            data.put("permissions", request.getPermissions() != null ? request.getPermissions() : List.of());
            data.put("addedBy", addedBy);
            data.put("createdAt", FieldValue.serverTimestamp());

            firestore.collection(Constants.COL_CLUB_MEMBERS).document(email).set(data).get();
            roleCacheService.invalidate(email);

            return ClubMemberResponse.builder()
                    .email(email)
                    .clubId(request.getClubId())
                    .clubName(request.getClubName())
                    .department(request.getDepartment())
                    .permissions(request.getPermissions())
                    .addedBy(addedBy)
                    .build();

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to add club member");
        }
    }

    public ClubMemberResponse updatePermissions(String email, List<String> permissions) {
        String normalizedEmail = EmailUtils.normalize(email);
        try {
            DocumentReference docRef = firestore.collection(Constants.COL_CLUB_MEMBERS).document(normalizedEmail);
            DocumentSnapshot doc = docRef.get().get();
            if (!doc.exists()) {
                throw ApiException.notFound("Member not found");
            }

            docRef.update("permissions", permissions).get();
            roleCacheService.invalidate(normalizedEmail);

            DocumentSnapshot updated = docRef.get().get();
            return toResponse(updated);

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to update permissions");
        }
    }

    public void removeMember(String email) {
        String normalizedEmail = EmailUtils.normalize(email);
        try {
            firestore.collection(Constants.COL_CLUB_MEMBERS).document(normalizedEmail).delete().get();
            roleCacheService.invalidate(normalizedEmail);
        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to remove member");
        }
    }

    @SuppressWarnings("unchecked")
    private ClubMemberResponse toResponse(DocumentSnapshot doc) {
        return ClubMemberResponse.builder()
                .email(doc.getString("email"))
                .clubId(doc.getString("clubId"))
                .clubName(doc.getString("clubName"))
                .department(doc.getString("department"))
                .permissions((List<String>) doc.get("permissions"))
                .addedBy(doc.getString("addedBy"))
                .createdAt(doc.get("createdAt"))
                .build();
    }
}
