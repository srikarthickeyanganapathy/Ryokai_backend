package com.example.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SM-M01 fix: spec requires rejection_reason NOT NULL enforced at DTO + DB level.
 * - DTO: @NotBlank added (was only @Size).
 * - Controller: TaskController.rejectTask now requires the body (@Valid, required=true).
 * - DB: V39 migration adds partial CHECK constraint
 *       chk_rejection_reason_when_rejected.
 */
public class RejectReasonDTO {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;

    public RejectReasonDTO() {}

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
