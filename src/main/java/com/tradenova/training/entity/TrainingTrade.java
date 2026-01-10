package com.tradenova.training.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 훈련 세션 중에 사용자가 버튼을 눌러 발생시킨 매수/매도 체결 로그 1건
 */
@Getter
@Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "training_trade",
        indexes = {
                @Index(name="idx_trade_session", columnList="session_id"),
                @Index(name="idx_trade_account", columnList="account_id")
        }
)
public class TrainingTrade {

    /**
     * 매매 기록의 고유 ID (PK)
     * 한 번 체결된 주문 1건을 식별하기 위한 값
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 어떤 훈련 세션에서 발생한 매매인지
     * TrainingSession.id 와 연결되는 값
     */
    @Column(name="session_id", nullable=false)
    private Long sessionId;

    /**
     * 어떤 모의투자 계좌에서 발생한 매매인지
     * PaperAccount.id 와 연결
     */
    @Column(name="account_id", nullable=false)
    private Long accountId;

    /**
     * 매수(BUY)인지 매도(SELL)인지 구분
     */
    @Enumerated(EnumType.STRING)
    @Column(name="side", length=10, nullable=false)
    private TradeSide side;

    /**
     * 체결 가격
     * 소수점 4자리까지 허용 (주식/ETF/파생 대응)
     */
    @Column(name="price", precision=18, scale=4, nullable=false)
    private BigDecimal price;

    /**
     * 체결 수량
     * 소수점 허용 -> 코인/파생상품까지 확장 가능
     */
    @Column(name="qty", precision=18, scale=6, nullable=false)
    private BigDecimal qty;

    /**
     * 매매 체결이 기록된 시각
     * 훈련에서는 "차트 봉 날짜"와 실제 체결 시각이 다를 수 있어서 분리
     * MVP 단계에서는 이 값만 써도 충분
     */
    @CreationTimestamp
    @Column(name="created_at", nullable=false)
    private OffsetDateTime createdAt;
}
