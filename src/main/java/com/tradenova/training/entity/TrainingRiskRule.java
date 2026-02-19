package com.tradenova.training.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "training_risk_rule",
        uniqueConstraints = {
                // 하나의 훈련 세션(session_id)에
                // 리스크 규칙은 반드시 1개만 존재하도록 보장
                @UniqueConstraint(
                        name = "uk_risk_rule_chart",
                        columnNames = {"chart_id"}
                )
        }
)
public class TrainingRiskRule {

    /**
     * 리스크 규칙 자체의 고유 ID (PK)
     * 내부 식별용
     */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**  어떤 차트에 적용되는 리스크룰인지 */
    @Column(name="chart_id", nullable=false)
    private Long chartId;

    /**
     * 어떤 모의투자 계좌 기준의 리스크 규칙인지
     * (계좌 단위 확장 가능성 고려)
     * PaperAccount.id 와 연결
     */
    @Column(name="account_id", nullable=false)
    private Long accountId;

    /**
     * 손절 가격
     * null 이면 손절 미설정 상태
     */
    @Column(name="stop_loss_price", precision=18, scale=4)
    private BigDecimal stopLossPrice;

    /**
     * 익절 가격
     * null 이면 익절 미설정 상태
     */
    @Column(name="take_profit_price", precision=18, scale=4)
    private BigDecimal takeProfitPrice;

    /**
     * 리스크 규칙 활성화 여부
     * true -> 자동 손절/익절 적용
     * false -> 규칙은 저장돼 있지만 현재는 비활성
     */
    @Column(name="enabled", nullable=false)
    private boolean enabled;

    /**
     * 리스크 규칙이 마지막으로 수정된 시각
     * (값 변경 시 자동 갱신)
     */
    @UpdateTimestamp
    @Column(name="updated_at")
    private OffsetDateTime updatedAt;
}
