package com.tradenova.waitlist.dto;

import com.tradenova.waitlist.entity.BetaWaitlist;
import java.time.OffsetDateTime;

public record BetaWaitlistResponse(Long id, String email, OffsetDateTime createdAt) {
    public static BetaWaitlistResponse from(BetaWaitlist waitlist) {
        return new BetaWaitlistResponse(waitlist.getId(), waitlist.getEmail(), waitlist.getCreatedAt());
    }
}
