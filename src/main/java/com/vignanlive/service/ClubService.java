package com.vignanlive.service;

import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.vignanlive.dto.ChangeHeadRequest;
import com.vignanlive.dto.ClubRequest;
import com.vignanlive.dto.ClubResponse;
import com.vignanlive.exception.ApiException;
import com.vignanlive.service.RoleCacheService;
import com.vignanlive.util.Constants;
import com.vignanlive.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClubService {

    private final Firestore firestore;
    private final FirebaseAuth firebaseAuth;
    private final RoleCacheService roleCacheService;

    public List<ClubResponse> listClubs(String department) {
        try {
            Query query;
            if (department != null) {
                query = firestore.collection(Constants.COL_CLUBS)
                        .whereEqualTo("department", department)
                        .orderBy("createdAt", Query.Direction.DESCENDING);
            } else {
                query = firestore.collection(Constants.COL_CLUBS)
                        .orderBy("createdAt", Query.Direction.DESCENDING);
            }

            return query.get().get().getDocuments().stream()
                    .map(this::toClubResponse)
                    .toList();

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch clubs");
        }
    }

    public List<ClubResponse> listAllClubs() {
        return listClubs(null);
    }

    public ClubResponse getClub(String clubId) {
        try {
            DocumentSnapshot doc = firestore.collection(Constants.COL_CLUBS)
                    .document(clubId).get().get();
            if (!doc.exists()) {
                throw ApiException.notFound("Club not found");
            }
            return toClubResponse(doc);
        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch club");
        }
    }

    public ClubResponse createClub(ClubRequest request, String createdBy) {
        String headEmail = EmailUtils.normalize(request.getHeadEmail());
        EmailUtils.validateEmail(headEmail);

        try {
            // 1. Create Firebase Auth account for head
            try {
                UserRecord.CreateRequest createReq = new UserRecord.CreateRequest()
                        .setEmail(headEmail)
                        .setPassword(request.getHeadPassword());
                firebaseAuth.createUser(createReq);
            } catch (FirebaseAuthException e) {
                if (!"EMAIL_ALREADY_EXISTS".equals(e.getAuthErrorCode().name())) {
                    throw ApiException.badRequest("Failed to create head account: " + e.getMessage());
                }
            }

            // 2. Create club document
            Map<String, Object> clubData = new HashMap<>();
            clubData.put("name", request.getName());
            clubData.put("department", request.getDepartment());
            clubData.put("headEmail", headEmail);
            clubData.put("upiId", request.getUpiId());
            clubData.put("description", request.getDescription());
            clubData.put("createdBy", createdBy);
            clubData.put("createdAt", FieldValue.serverTimestamp());

            DocumentReference clubRef = firestore.collection(Constants.COL_CLUBS).add(clubData).get();
            String clubId = clubRef.getId();

            // 3. Create club_heads document
            Map<String, Object> headData = new HashMap<>();
            headData.put("email", headEmail);
            headData.put("clubId", clubId);
            headData.put("clubName", request.getName());
            headData.put("upiId", request.getUpiId());
            headData.put("department", request.getDepartment());
            headData.put("addedBy", createdBy);
            headData.put("createdAt", FieldValue.serverTimestamp());

            firestore.collection(Constants.COL_CLUB_HEADS).document(headEmail).set(headData).get();
            roleCacheService.invalidate(headEmail);

            clubData.put("id", clubId);
            return ClubResponse.builder()
                    .id(clubId)
                    .name(request.getName())
                    .department(request.getDepartment())
                    .headEmail(headEmail)
                    .upiId(request.getUpiId())
                    .description(request.getDescription())
                    .createdBy(createdBy)
                    .build();

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to create club");
        }
    }

    public ClubResponse updateClub(String clubId, ClubRequest request) {
        try {
            DocumentReference clubRef = firestore.collection(Constants.COL_CLUBS).document(clubId);
            DocumentSnapshot clubDoc = clubRef.get().get();
            if (!clubDoc.exists()) {
                throw ApiException.notFound("Club not found");
            }

            Map<String, Object> updates = new HashMap<>();
            if (request.getName() != null) updates.put("name", request.getName());
            if (request.getUpiId() != null) updates.put("upiId", request.getUpiId());
            if (request.getDescription() != null) updates.put("description", request.getDescription());

            clubRef.update(updates).get();

            // Sync to club head doc
            String headEmail = clubDoc.getString("headEmail");
            if (headEmail != null) {
                Map<String, Object> headUpdates = new HashMap<>();
                if (request.getName() != null) headUpdates.put("clubName", request.getName());
                if (request.getUpiId() != null) headUpdates.put("upiId", request.getUpiId());
                if (!headUpdates.isEmpty()) {
                    firestore.collection(Constants.COL_CLUB_HEADS).document(headEmail)
                            .update(headUpdates).get();
                    roleCacheService.invalidate(headEmail);
                }
            }

            DocumentSnapshot updated = clubRef.get().get();
            return toClubResponse(updated);

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to update club");
        }
    }

    public ClubResponse changeHead(String clubId, ChangeHeadRequest request, String changedBy) {
        String newEmail = EmailUtils.normalize(request.getNewHeadEmail());
        EmailUtils.validateEmail(newEmail);

        try {
            DocumentReference clubRef = firestore.collection(Constants.COL_CLUBS).document(clubId);
            DocumentSnapshot clubDoc = clubRef.get().get();
            if (!clubDoc.exists()) {
                throw ApiException.notFound("Club not found");
            }

            String oldHeadEmail = clubDoc.getString("headEmail");
            String clubName = clubDoc.getString("name");
            String department = clubDoc.getString("department");
            String upiId = clubDoc.getString("upiId");

            // 1. Create Firebase account for new head
            try {
                UserRecord.CreateRequest createReq = new UserRecord.CreateRequest()
                        .setEmail(newEmail)
                        .setPassword(request.getNewHeadPassword());
                firebaseAuth.createUser(createReq);
            } catch (FirebaseAuthException e) {
                if (!"EMAIL_ALREADY_EXISTS".equals(e.getAuthErrorCode().name())) {
                    throw ApiException.badRequest("Failed to create account: " + e.getMessage());
                }
            }

            // 2. Delete old head doc
            if (oldHeadEmail != null) {
                firestore.collection(Constants.COL_CLUB_HEADS).document(oldHeadEmail).delete().get();
                roleCacheService.invalidate(oldHeadEmail);
            }

            // 3. Create new head doc
            Map<String, Object> headData = new HashMap<>();
            headData.put("email", newEmail);
            headData.put("clubId", clubId);
            headData.put("clubName", clubName);
            headData.put("upiId", upiId);
            headData.put("department", department);
            headData.put("addedBy", changedBy);
            headData.put("createdAt", FieldValue.serverTimestamp());

            firestore.collection(Constants.COL_CLUB_HEADS).document(newEmail).set(headData).get();
            roleCacheService.invalidate(newEmail);

            // 4. Update club doc
            clubRef.update("headEmail", newEmail).get();

            DocumentSnapshot updated = clubRef.get().get();
            return toClubResponse(updated);

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to change club head");
        }
    }

    private ClubResponse toClubResponse(DocumentSnapshot doc) {
        return ClubResponse.builder()
                .id(doc.getId())
                .name(doc.getString("name"))
                .department(doc.getString("department"))
                .headEmail(doc.getString("headEmail"))
                .upiId(doc.getString("upiId"))
                .description(doc.getString("description"))
                .createdBy(doc.getString("createdBy"))
                .createdAt(doc.get("createdAt"))
                .build();
    }
}
