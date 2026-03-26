package com.vignanlive.util;

import com.vignanlive.exception.ApiException;

public final class EmailUtils {

    private EmailUtils() {}

    public static boolean isVBITEmail(String email) {
        return email != null && email.toLowerCase().endsWith(Constants.ALLOWED_DOMAIN);
    }

    public static String getRollNumber(String email) {
        if (email == null || !email.contains("@")) return "";
        return email.split("@")[0].toUpperCase();
    }

    public static String normalize(String email) {
        return email == null ? null : email.toLowerCase().trim();
    }

    public static void validateEmail(String email) {
        if (!isVBITEmail(email)) {
            throw ApiException.badRequest("Only @vbithyd.ac.in emails are allowed");
        }
    }
}
