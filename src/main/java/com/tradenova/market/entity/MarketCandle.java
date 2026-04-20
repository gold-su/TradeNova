package com.tradenova.market.entity;

import com.tradenova.symbol.entity.Symbol;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 원천 시장 데이터(일봉) 저장 엔티티
 *
 * 역할 :
 * - 외부 시세 API(KIS)에서 가져온 "원본" 일봉 데이터를 저장
 * - 훈련 세션 생성 전에 먼저 여기서 조회하는 캐시 창고 역할
 *
 * 주의 :
 * - training_session_candle 과 다름
 * - market_candle = 원본 창고
 * - training_session_candle = 세션용 복사본
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "market_candle",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_market_candle_symbol_date",
                        columnNames = {"symbol_id", "candle_date"}
                )
        },
        indexes = {
                // 특정 종목 + 날짜 범위 조회
                @Index(name = "idx_market_candle_symbol_date", columnList = "symbol_id, candle_date"),
                // 날짜 기준 조회
                @Index(name = "idx_market_candle_date", columnList = "candle_date")
        }
)
public class MarketCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 어떤 종목의 캔들인지
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "symbol_id", nullable = false)
    private Symbol symbol;

    /**
     * 일봉 날짜
     */
    @Column(name = "candle_date", nullable = false)
    private LocalDate candleDate;

    /**
     * 시가
     */
    @Column(name = "open_price", nullable = false)
    private Double openPrice;

    /**
     * 고가
     */
    @Column(name = "high_price", nullable = false)
    private Double highPrice;

    /**
     * 저가
     */
    @Column(name = "low_price", nullable = false)
    private Double lowPrice;

    /**
     * 종가
     */
    @Column(name = "close_price", nullable = false)
    private Double closePrice;

    /**
     * 거래량
     */
    @Column(name = "volume", nullable = false)
    private Long volume;

    /**
     * 저장 시간
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * 수정 시간
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
