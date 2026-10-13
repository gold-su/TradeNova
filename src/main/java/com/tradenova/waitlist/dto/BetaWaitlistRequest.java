package com.tradenova.waitlist.dto;

import com.tradenova.waitlist.entity.BetaWaitlist.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record BetaWaitlistRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull InvestmentExperience investmentExperience,
        @NotNull PrimaryChallenge primaryChallenge,
        @NotNull ParticipationIntent participationIntent,
        boolean smsOptIn,
        @Size(max = 20) String phoneNumber,
        @Valid Attribution attribution
) {
    private static final String KOREAN_MOBILE_PATTERN = "010\\d{8}";

    @AssertTrue(message = "문자 알림을 선택한 경우 올바른 휴대폰 번호가 필요합니다.")
    public boolean isSmsSelectionValid() {
        if (!smsOptIn) return true;
        if (phoneNumber == null) return false;
        return phoneNumber.replace("-", "").trim().matches(KOREAN_MOBILE_PATTERN);
    }

    public record Attribution(
            @Size(max = 100) String utmSource,
            @Size(max = 100) String utmMedium,
            @Size(max = 100) String utmCampaign,
            @Size(max = 100) String utmContent
    ) {}
}
