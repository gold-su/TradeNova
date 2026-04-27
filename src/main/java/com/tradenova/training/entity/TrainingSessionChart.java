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
        indexes = {
                @Index(name = "idx_session_chart_session", columnList = "session_id"),
                @Index(name = "idx_session_chart_symbol", columnList = "symbol_id"),
                @Index(name = "idx_session_chart_session_active", columnList = "session_id, active"),
                @Index(name = "idx_session_chart_session_idx_active", columnList = "session_id, chart_index, active")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TrainingChartStatus status = TrainingChartStatus.IN_PROGRESS;

    /**
     * 현재 세션 화면에 노출되는 활성 차트 여부
     *
     * 새로고침 전 차트는 active=false
     * 새로고침 후 새 차트는 active=true
     */
    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * 새로고침으로 생성된 차트인지 여부
     *
     * 세션 최초 생성 차트: false
     * 새로고침으로 교체된 차트: true
     */
    @Builder.Default
    @Column(name = "refreshed", nullable = false)
    private boolean refreshed = false;

    // ===== 편의 메서드 =====
    public void setProgressIndex(int progressIndex) {
        this.progressIndex = progressIndex;
    }

    public void complete() {
        this.status = TrainingChartStatus.COMPLETED;
    }

    public void deactivate() {
        this.active = false;
    }

    public void markRefreshed() {
        this.refreshed = true;
    }
}
