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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 예: KOSPI, KOSDAQ, NASDAQ 등
    @Column(name = "market", length = 20, nullable = false)
    private String market;

    // 예: "005930"
    @Column(name = "ticker", length = 50, nullable = false)
    private String ticker;

    // 예: "삼성전자"
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    // 예: KRW, USD (단순 문자열로 시작)
    @Column(name = "currency", length = 10, nullable = false)
    private String currency;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

}
