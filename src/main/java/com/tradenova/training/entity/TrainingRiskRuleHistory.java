package com.tradenova.training.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 리스크 룰이 저장될 때마다 당시 상태를 보존하는 append-only 이력.
 */
@Entity
@Table(
        name = "training_risk_rule_history",
        indexes = {
                @Index(name = "idx_risk_history_chart_id", columnList = "chart_id, id"),
                @Index(name = "idx_risk_history_user_created", columnList = "user_id, created_at")
        }
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainingRiskRuleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "risk_rule_id", nullable = false)
    private Long riskRuleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "chart_id", nullable = false)
    private Long chartId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "stop_loss_price", precision = 18, scale = 4)
    private BigDecimal stopLossPrice;

    @Column(name = "take_profit_price", precision = 18, scale = 4)
    private BigDecimal takeProfitPrice;

    @Column(name = "auto_exit_enabled", nullable = false)
    private boolean autoExitEnabled;

    @Column(name = "progress_index", nullable = false)
    private Integer progressIndex;

    @Column(name = "candle_time", nullable = false)
    private Long candleTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
