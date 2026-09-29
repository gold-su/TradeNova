package com.tradenova.waitlist.dto;

import com.tradenova.waitlist.entity.BetaWaitlist.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record BetaWaitlistRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull InvestmentExperience investmentExperience,
        @NotNull PrimaryChallenge primaryChallenge,
        @NotNull ParticipationIntent participationIntent,
        @Valid Attribution attribution
) {
    public record Attribution(
            @Size(max = 100) String utmSource,
            @Size(max = 100) String utmMedium,
            @Size(max = 100) String utmCampaign,
            @Size(max = 100) String utmContent
    ) {}
}
