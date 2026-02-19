package com.tradenova.training.entity;

import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;


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

    // 훈련 모드
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 20, nullable = false)
    private TrainingMode mode;

    // 세션 상태
    // READY / ONGOING / FINISHED 등
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingStatus status;

    // 생성 시각
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // 멀티차트 핵심: 세션은 차트 묶음을 가진다
    // 1:N 관계, Session(부모)(1) : SessionChart(자식)(N)
    // mappedBy = "session"는 연관관계의 주인은 SessionChart 쪽이라는 뜻.
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("chartIndex ASC")
    @Builder.Default
    private List<TrainingSessionChart> charts = new ArrayList<>();
}
