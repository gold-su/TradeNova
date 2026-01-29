package com.tradenova.paper.entity;

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
        name = "paper_position",
        uniqueConstraints = {
                // 한 계좌에 한 종목 포지션은 1개만 유지
                @UniqueConstraint(name = "uk_position_account_symbol", columnNames = {"account_id", "symbol_id"})
        },
        indexes = { //index << 칼럼 기준 빠른 조회를 위한 힌트
                //계좌별 포지션 조회 성능용
                @Index(name = "idx_position_account", columnList = "account_id") //paper_position 테이블에서 account_id로 자주 조회할 거니까 미리 정렬된 구조 하나 만들어 두라는 의미
        }
)
//보유 종목 상태 엔티티
public class PaperPosition {

    //포지션 고유 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    // 어떤 계좌의 포지션인지 (계좌 1 : 포지션 N)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private PaperAccount account;

    // 지금은 symbol 테이블이 없으니 Long 으로, symbol 엔티티 만들면 ManyToOne 으로 바꿔도 됨
    // 보유 중인 종목 ID (지금은 단순 ID, 나중에 Symbol 엔티티로 확장 가능)
    @Column(name = "symbol_id", nullable = false)
    private Long symbolId;

    // 현재 보유 수량 (소수점 대응: ETF, 코인 확장 대비)
    @Column(name = "quantity", precision = 18, scale = 6, nullable = false)
    private BigDecimal quantity;

    // 평균 매입 단가 (분할 매수 반영)
    @Column(name = "avg_price", precision = 18, scale = 4, nullable = false)
    private BigDecimal avgPrice;

    // 포지션 변경 시각 (매수/매도 시 자동 갱신)
    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
