package com.vignanlive.service;

import com.google.cloud.firestore.*;
import com.vignanlive.dto.MemberRequestDto;
import com.vignanlive.exception.ApiException;
import com.vignanlive.util.Constants;
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
public class MemberRequestService {

    private final Firestore firestore;
    private final ClubMemberService clubMemberService;

    @SuppressWarnings("unchecked")
    public MemberRequestDto createRequest(MemberRequestDto request, String requestedBy) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", request.getType());
            data.put("memberEmail", request.getMemberEmail());
            data.put("clubId", request.getClubId());
            data.put("clubName", request.getClubName());
            data.put("department", request.getDepartment());
            data.put("permissionsToRevoke", request.getPermissionsToRevoke());
            data.put("reason", request.getReason());
            data.put("requestedBy", requestedBy);
            data.put("status", Constants.STATUS_PENDING);
            data.put("createdAt", FieldValue.serverTimestamp());

            DocumentReference docRef = firestore.collection(Constants.COL_MEMBER_REQUESTS).add(data).get();

            return MemberRequestDto.builder()
                    .id(docRef.getId())
                    .type(request.getType())
                    .memberEmail(request.getMemberEmail())
                    .clubId(request.getClubId())
                    .clubName(request.getClubName())
                    .department(request.getDepartment())
                    .permissionsToRevoke(request.getPermissionsToRevoke())
                    .reason(request.getReason())
                    .requestedBy(requestedBy)
                    .status(Constants.STATUS_PENDING)
                    .build();

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to create member request");
        }
    }

    public List<MemberRequestDto> listRequests(String clubId) {
        try {
            Query query = firestore.collection(Constants.COL_MEMBER_REQUESTS)
                    .whereEqualTo("clubId", clubId)
                    .orderBy("createdAt", Query.Direction.DESCENDING);

            return query.get().get().getDocuments().stream()
                    .map(this::toDto)
                    .toList();

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to fetch member requests");
        }
    }

    @SuppressWarnings("unchecked")
    public MemberRequestDto approveRequest(String requestId, String reviewedBy) {
        try {
            DocumentReference docRef = firestore.collection(Constants.COL_MEMBER_REQUESTS).document(requestId);
            DocumentSnapshot doc = docRef.get().get();
            if (!doc.exists()) {
                throw ApiException.notFound("Request not found");
            }

            String type = doc.getString("type");
            String memberEmail = doc.getString("memberEmail");

            // Execute the action
            if ("remove_member".equals(type)) {
                clubMemberService.removeMember(memberEmail);
            } else if ("revoke_permission".equals(type)) {
                List<String> toRevoke = (List<String>) doc.get("permissionsToRevoke");
                if (toRevoke != null && memberEmail != null) {
                    // Get current permissions and remove the revoked ones
                    DocumentSnapshot memberDoc = firestore.collection(Constants.COL_CLUB_MEMBERS)
                            .document(memberEmail.toLowerCase()).get().get();
                    if (memberDoc.exists()) {
                        List<String> current = (List<String>) memberDoc.get("permissions");
                        if (current != null) {
                            List<String> updated = current.stream()
                                    .filter(p -> !toRevoke.contains(p))
                                    .toList();
                            clubMemberService.updatePermissions(memberEmail, updated);
                        }
                    }
                }
            }

            // Update request status
            docRef.update(
                    "status", "approved",
                    "reviewedBy", reviewedBy,
                    "reviewedAt", FieldValue.serverTimestamp()
            ).get();

            DocumentSnapshot updated = docRef.get().get();
            return toDto(updated);

        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof ApiException) throw (ApiException) e.getCause();
            throw ApiException.internal("Failed to approve request");
        }
    }

    public MemberRequestDto rejectRequest(String requestId, String reviewedBy) {
        try {
            DocumentReference docRef = firestore.collection(Constants.COL_MEMBER_REQUESTS).document(requestId);
            DocumentSnapshot doc = docRef.get().get();
            if (!doc.exists()) {
                throw ApiException.notFound("Request not found");
            }

            docRef.update(
                    "status", "rejected",
                    "reviewedBy", reviewedBy,
                    "reviewedAt", FieldValue.serverTimestamp()
            ).get();

            DocumentSnapshot updated = docRef.get().get();
            return toDto(updated);

        } catch (InterruptedException | ExecutionException e) {
            throw ApiException.internal("Failed to reject request");
        }
    }

    @SuppressWarnings("unchecked")
    private MemberRequestDto toDto(DocumentSnapshot doc) {
        return MemberRequestDto.builder()
                .id(doc.getId())
                .type(doc.getString("type"))
                .memberEmail(doc.getString("memberEmail"))
                .clubId(doc.getString("clubId"))
                .clubName(doc.getString("clubName"))
                .department(doc.getString("department"))
                .permissionsToRevoke((List<String>) doc.get("permissionsToRevoke"))
                .reason(doc.getString("reason"))
                .requestedBy(doc.getString("requestedBy"))
                .status(doc.getString("status"))
                .reviewedBy(doc.getString("reviewedBy"))
                .reviewedAt(doc.get("reviewedAt"))
                .createdAt(doc.get("createdAt"))
                .build();
    }
}
