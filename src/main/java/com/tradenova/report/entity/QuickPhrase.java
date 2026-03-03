package com.tradenova.report.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name="quick_phrase",
        // 사용자별 문장 목록을 정렬해서 빠르게 조회
        indexes = @Index(name="idx_phrase_user_sort", columnList="user_id, sort_order, id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 사용자가 자주 쓰는 리포트 문장을 저장해두는 테이블
 * “지지선 근처라 분할매수”
 * “거래량 동반 돌파”
 * “손절가 미설정 위험”
 */
public class QuickPhrase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 문장 소유자
    @Column(name="user_id", nullable=false)
    private Long userId;

    // 버튼에 표시되는 짧은 제목
    /**
     * "지지선"
     * "돌파"
     * "손절"
     */
    @Column(nullable=false, length=40)
    private String title;
    
    // 실제 리포트에 삽입될 문장 내용
    // "최근 거래량이 증가하며 전고점 돌파 시도 중이라 분할 매수 진입"
    @Column(nullable=false, columnDefinition="TEXT")
    private String content;
    
    // 버튼 표시 순서
    @Column(name="sort_order")
    private Integer sortOrder;
    
    // 생성 시 자동 기록
    @CreationTimestamp
    private Instant createdAt;
    
    // 수정 시 자동 갱신
    @UpdateTimestamp
    private Instant updatedAt;
}
