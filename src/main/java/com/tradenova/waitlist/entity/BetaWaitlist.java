package com.tradenova.waitlist.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "beta_waitlist", uniqueConstraints = @UniqueConstraint(name = "uq_beta_waitlist_email", columnNames = "email"))
public class BetaWaitlist {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 254) private String email;
    @Enumerated(EnumType.STRING) @Column(name = "investment_experience", nullable = false, length = 40) private InvestmentExperience investmentExperience;
    @Enumerated(EnumType.STRING) @Column(name = "primary_challenge", nullable = false, length = 40) private PrimaryChallenge primaryChallenge;
    @Enumerated(EnumType.STRING) @Column(name = "participation_intent", nullable = false, length = 20) private ParticipationIntent participationIntent;
    @Column(name = "sms_opt_in", nullable = false) private boolean smsOptIn;
    @Column(name = "phone_number", length = 11) private String phoneNumber;
    @Column(name = "utm_source", length = 100) private String utmSource;
    @Column(name = "utm_medium", length = 100) private String utmMedium;
    @Column(name = "utm_campaign", length = 100) private String utmCampaign;
    @Column(name = "utm_content", length = 100) private String utmContent;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;

    public enum InvestmentExperience { NONE, UNDER_6_MONTHS, SIX_MONTHS_TO_TWO_YEARS, OVER_TWO_YEARS }
    public enum PrimaryChallenge { ENTRY_DECISION, EXIT_DECISION, PLAN_DISCIPLINE, EMOTIONAL_TRADING, CHART_ANALYSIS, OTHER }
    public enum ParticipationIntent { YES, CONSIDERING }
}
