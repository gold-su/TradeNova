package com.tradenova.paper.entity;

import com.tradenova.paper.dto.BaseCurrency;
import com.tradenova.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
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
        name = "paper_account",
        indexes = {
                @Index(name = "idx_paper_account_user", columnList = "user_id")
        }
)
//계좌 엔티티
public class PaperAccount {

    //계좌 고유 ID (DB에서 자동 증가)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 계좌의 주인 (한 유저가 여러 계좌 가질 수 있음)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    //계좌 이름
    @Column(length = 50, nullable = false)
    private String name;

    // 계좌 설명(전략/룰/목표/주의사항 등 메모)
    @Column(name = "description", length = 500)
    private String description;

    // 계좌 생성 시 초기 자본금 (리셋 기준값)
    @Column(name = "initial_balance", precision = 18, scale = 2, nullable = false)
    private BigDecimal initialBalance;

    // 현재 사용 가능한 현금 (매수/매도 후 변동)
    @Column(name = "cash_balance", precision = 18, scale = 2, nullable = false)
    private BigDecimal cashBalance;

    // 기준 통화 (KRW, USD 등)
    @Enumerated(EnumType.STRING)
    @Column(name = "base_currency", length = 10, nullable = false)
    private BaseCurrency baseCurrency;

    // 대표 계좌 여부 (로그인 후 기본 선택 계좌)
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    // 계좌 생성 시각 (자동 기록)
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // 계좌 정보 마지막 수정 시각 (자동 갱신)
    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** 리셋: 현금을 초기시드로 되돌림 */
    public void resetCash() {
        this.cashBalance = this.initialBalance;
    }
    // 연습 초기화용: 현재 현금을 처음 금액으로 되돌림
}
