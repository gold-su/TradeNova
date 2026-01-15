package com.tradenova.training.entity;

import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;


/**
 * 트레이딩 훈련 세션 엔티티
 * - 유저가 특정 계좌/종목/기간으로 진행하는 "연습 한 판"
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "training_session", //테이블명
        indexes = {
                //유저별 세션 조회 성능용
                @Index(name = "idx_session_user", columnList = "user_id"),
                //계좌별 세션 조회 성능용
                @Index(name = "idx_session_account", columnList = "account_id"),
                //종목별 세션 조회 성능용
                @Index(name = "idx_session_symbol", columnList = "symbol_id")
        }
)
public class TrainingSession {

    // 세션 고유 ID (PK)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 세션 주인 (누가 이 훈련을 하는지)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 어떤 연습계좌로 훈련하는지 (필수)
    // (자금, 포지션 상태를 공유)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private PaperAccount account;

    // 어떤 종목으로 훈련하는지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol_id", nullable = false)
    private Symbol symbol;

    // 현재가 계산용
    @Column(name = "progress_index", nullable = false)
    private Integer progressIndex;

    // 훈련 모드
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 20, nullable = false)
    private TrainingMode mode;

    // 랜덤 훈련 구간 (일봉 기준)
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate; // 훈련 시작 날짜 (YYYY-MM-DD)

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate; // 훈련 종료 날짜

    // 일 봉 몇개를 보여줄지 (=bars)
    @Column(name = "bars", nullable = false)
    private Integer bars;

    // “미래 봉 숨기기/되감기” 같은 기능을 위해 남겨두는 옵션(지금은 0 고정해도 됨)
    @Column(name = "hidden_future_bars", nullable = false)
    private Integer hiddenFutureBars;

    // 세션 상태
    // READY / ONGOING / FINISHED 등
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingStatus status;

    // 생성 시각
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
