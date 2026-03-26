package com.vignanlive.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BulkVerifyResponse {
    private int matched;
    private int amountMismatch;
    private int unmatched;
    private int verified;
    private List<VerifyDetail> details;

    @Data
    @Builder
    public static class VerifyDetail {
        private String bookingId;
        private String transactionId;
        private String status; // matched, amount_mismatch, not_found, no_utr
        private String message;
    }
}
