package com.tradenova.waitlist.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.waitlist.dto.BetaWaitlistRequest;
import com.tradenova.waitlist.dto.BetaWaitlistResponse;
import com.tradenova.waitlist.entity.BetaWaitlist;
import com.tradenova.waitlist.repository.BetaWaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BetaWaitlistService {
    private final BetaWaitlistRepository repository;

    @Transactional
    public BetaWaitlistResponse create(BetaWaitlistRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new CustomException(ErrorCode.WAITLIST_EMAIL_ALREADY_EXISTS);
        }

        BetaWaitlistRequest.Attribution attribution = request.attribution();
        BetaWaitlist waitlist = BetaWaitlist.builder()
                .email(email)
                .investmentExperience(request.investmentExperience())
                .primaryChallenge(request.primaryChallenge())
                .participationIntent(request.participationIntent())
                .smsOptIn(request.smsOptIn())
                .phoneNumber(normalizePhoneNumber(request.smsOptIn(), request.phoneNumber()))
                .utmSource(normalize(attribution == null ? null : attribution.utmSource()))
                .utmMedium(normalize(attribution == null ? null : attribution.utmMedium()))
                .utmCampaign(normalize(attribution == null ? null : attribution.utmCampaign()))
                .utmContent(normalize(attribution == null ? null : attribution.utmContent()))
                .build();

        try {
            return BetaWaitlistResponse.from(repository.saveAndFlush(waitlist));
        } catch (DataIntegrityViolationException ex) {
            throw new CustomException(ErrorCode.WAITLIST_EMAIL_ALREADY_EXISTS);
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizePhoneNumber(boolean smsOptIn, String value) {
        if (!smsOptIn || value == null) return null;
        return value.replace("-", "").trim();
    }
}
