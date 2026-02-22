package com.tradenova.training.entity;

import com.tradenova.symbol.entity.Symbol;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "training_session_chart",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_session_chart_session_idx", columnNames = {"session_id", "chart_index"})
        },
        indexes = {
                @Index(name = "idx_session_chart_session", columnList = "session_id"),
                @Index(name = "idx_session_chart_symbol", columnList = "symbol_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TrainingSessionChart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // CHANGED: 동시성(연타/중복요청) 방어용 버전 컬럼 추가
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // 어떤 세션의 차트인지
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TrainingSession session;

    // 0~3 (Free/Pro/Premium에 따라 사용 개수 달라짐)
    @Column(name = "chart_index", nullable = false)
    private Integer chartIndex;

    // 이 차트가 어떤 종목인지
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "symbol_id", nullable = false)
    private Symbol symbol;

    // 이 차트의 기간
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // 공개할 봉 개수
    @Column(name = "bars", nullable = false)
    private Integer bars;

    // 치팅 방지용: 미래 봉 숨김
    @Column(name = "hidden_future_bars", nullable = false)
    private Integer hiddenFutureBars;

    // 진행도(현재 공개된 idx)
    @Column(name = "progress_index", nullable = false)
    private Integer progressIndex;

    @CreationTimestamp
    @Column(name="created_at", nullable=false, updatable = false)
    private OffsetDateTime createdAt;

    // ===== 편의 메서드 =====
    public void setProgressIndex(int progressIndex) {
        this.progressIndex = progressIndex;
    }
}
