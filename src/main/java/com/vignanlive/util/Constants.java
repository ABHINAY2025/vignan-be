package com.vignanlive.util;

import java.util.List;

public final class Constants {

    private Constants() {}

    public static final String ALLOWED_DOMAIN = "@vbithyd.ac.in";

    public static final List<String> DEPARTMENTS = List.of(
            "CSE", "ECE", "EEE", "MECH", "CIVIL", "IT", "AIDS", "AIML", "CSM"
    );

    // Roles
    public static final String ROLE_MASTER_ADMIN = "master_admin";
    public static final String ROLE_SUPER_ADMIN = "super_admin";
    public static final String ROLE_CLUB_HEAD = "club_head";
    public static final String ROLE_CLUB_MEMBER = "club_member";
    public static final String ROLE_STUDENT = "student";

    // Role hierarchy (higher index = higher privilege)
    public static final List<String> ROLE_HIERARCHY = List.of(
            ROLE_STUDENT, ROLE_CLUB_MEMBER, ROLE_CLUB_HEAD, ROLE_SUPER_ADMIN, ROLE_MASTER_ADMIN
    );

    // Permissions
    public static final String PERM_VERIFY_PAYMENTS = "verify_payments";
    public static final String PERM_MANAGE_ATTENDANCE = "manage_attendance";
    public static final String PERM_VIEW_ANALYTICS = "view_analytics";

    // Firestore collections
    public static final String COL_MASTER_ADMINS = "master_admins";
    public static final String COL_SUPER_ADMINS = "super_admins";
    public static final String COL_CLUB_HEADS = "club_heads";
    public static final String COL_CLUB_MEMBERS = "club_members";
    public static final String COL_USERS = "users";
    public static final String COL_CLUBS = "clubs";
    public static final String COL_EVENTS = "events";
    public static final String COL_BOOKINGS = "bookings";
    public static final String COL_MEMBER_REQUESTS = "member_requests";
    public static final String COL_USER_DEVICES = "user_devices";
    public static final String COL_NOTIFICATIONS = "notifications";

    // Event statuses
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";

    // Payment statuses
    public static final String PAY_CONFIRMED = "confirmed";
    public static final String PAY_PENDING = "pending_verification";
    public static final String PAY_REJECTED = "rejected";

    // Audience types
    public static final String AUDIENCE_ALL = "all";
    public static final String AUDIENCE_DEPARTMENT = "department";

    public static boolean hasMinRole(String userRole, String requiredRole) {
        int userIndex = ROLE_HIERARCHY.indexOf(userRole);
        int requiredIndex = ROLE_HIERARCHY.indexOf(requiredRole);
        return userIndex >= requiredIndex;
    }
}
