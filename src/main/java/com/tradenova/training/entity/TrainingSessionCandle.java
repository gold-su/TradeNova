package com.tradenova.training.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 훈련 세션 전용 캔들 (세션 생성 시 1회 확정 저장)
 *
 * 특징:
 * - session_id + idx 로 하나의 봉을 유일하게 식별
 * - idx는 0부터 시작 (bars-1 까지)
 * - 이 테이블이 "훈련의 진실 데이터"가 됨 (치팅 방지 핵심)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "training_session_candle",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_training_session_candle_chart_idx",
                        columnNames = {"chart_id", "idx"}
                )
        },
        indexes = {
                @Index(name = "idx_training_session_candle_chart", columnList = "chart_id"),
                @Index(name = "idx_training_session_candle_time", columnList = "t")
        }
)
public class TrainingSessionCandle {

    /** 내부 PK (의미 없음, JPA 식별용) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어떤 차트(chart)의 캔들인지 */
    @Column(name = "chart_id", nullable = false)
    private Long chartId;

    /**
     * 세션 내 봉 인덱스 (0부터 시작)
     * - progressIndex와 직접 비교되는 값
     */
    @Column(name = "idx", nullable = false)
    private Integer idx;

    /**
     * 봉 기준 시각 (epoch millis)
     * - Asia/Seoul 기준 00:00:00
     * - 차트 시간축 / 리포트 공통 기준
     */
    @Column(name = "t", nullable = false)
    private Long t;

    /** 시가 */
    @Column(name = "o", nullable = false)
    private Double o;

    /** 고가 */
    @Column(name = "h", nullable = false)
    private Double h;

    /** 저가 */
    @Column(name = "l", nullable = false)
    private Double l;

    /** 종가 (currentPrice 계산의 기준) */
    @Column(name = "c", nullable = false)
    private Double c;

    /** 거래량 */
    @Column(name = "v", nullable = false)
    private Long v;

    /** 저장 시각 (디버깅/감사용) */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

}
