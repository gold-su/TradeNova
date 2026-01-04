package com.tradenova.symbol.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "symbol",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_symbol_market_ticker", columnNames = {"market", "ticker"}) //(market, ticker)는 유일
        },
        indexes = {
                @Index(name = "idx_symbol_ticker", columnList = "ticker"), //종목 검색
                @Index(name = "idx_symbol_active", columnList = "active")  //현재 거래 가능한 종목만
        }
)
public class Symbol {

    //내부 시스템용 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 예: KOSPI, KOSDAQ, NASDAQ 등
    // 나중엔 enum 으로 변경해도 됨
    @Column(name = "market", length = 20, nullable = false)
    private String market;

    // 예: "005930", KIS에서 쓰는 식별자
    @Column(name = "ticker", length = 50, nullable = false)
    private String ticker;

    // 예: "삼성전자"
    // UI 표시용
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    // 예: KRW, USD (단순 문자열로 시작)
    // 계좌 통화(BaseCurrency)랑 매칭
    @Column(name = "currency", length = 10, nullable = false)
    private String currency;

    /**
     * true -> 거래/훈련 가능
     * false -> 상장폐지 / 일시 중단 / 비공개
     * -> 테이터는 유지하되 사용만 제한하는 용도 (실무에선 삭제보단 훨씬 안전)
     */
    @Column(name = "active", nullable = false)
    private boolean active;

    // 언제 종목이 등록됐는지
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // 언제 종목이 수정됐는지
    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

}
