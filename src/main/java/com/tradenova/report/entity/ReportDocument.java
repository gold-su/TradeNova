package com.tradenova.report.entity;


import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.common.jpa.JsonNodeConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "report_document",
        indexes = {
                // 특정 사용자 + 특정 차트 + 특정 종류 리토프 검색
                @Index(name="idx_report_user_chart_kind", columnList="user_id, chart_id, kind"),
                // 특정 사용자별 리포트 최신순 조회
                @Index(name="idx_report_user_created", columnList="user_id, created_at")
        },
        uniqueConstraints = {
                // Draft는 차트당 1개 고정 (Snapshot은 여러 개)
                @UniqueConstraint(name="uq_draft_user_chart", columnNames={"user_id","chart_id","kind"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * {
 *   "thesis": "내 관점 한 줄",
 *   "plan": {
 *     "entry": "진입 조건",
 *     "risk": { "stopLoss": 71000, "takeProfit": 78000, "rMultiple": 2.0 },
 *     "positionSizing": "분할/수량 근거"
 *   },
 *   "signals": [
 *     { "type": "MA", "note": "20/60 이평 정배열", "confidence": 3 },
 *     { "type": "VOLUME", "note": "거래량 증가" }
 *   ],
 *   "context": {
 *     "news": [{ "title": "...", "url": "..." }],
 *     "macro": "환율/금리/섹터",
 *     "fundamental": "재무 메모"
 *   },
 *   "journal": {
 *     "freeNote": "자유 메모",
 *     "tags": ["FOMO", "추격매수"]
 *   }
 * }
 */
public class ReportDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 리포트 소유자
    @Column(name="user_id",nullable = false)
    private Long userId;

    // 어떤 차트의 리포트인지
    @Column(name="chart_id", nullable = false)
    private Long chartId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResportKind kind;

    // contentJson 구조 버전, 마이그레이션 전략 핵심
    @Column(nullable = false)
    private Integer schemaVersion;

    //Snapshot 순번, Draft는 항상 0, Snapshot은 1,2,3,4...
    @Column(nullable = false)
    private Integer version;

    // 특정 매수/매도 이벤트와 연결 가능
    // ex) "이 스냅샷은 3번째 매도 직후 작성됨"
    @Column(name = "linked_event_id")
    private Long linkedEventId;

    // 리포트 전체 데이터 저장 공간
    /**
     * 사용자 입력
     * AI 분석 결과
     * 통계
     * 감정 점수
     * 리스크 평가
     */
    // DB에는 LONGTEXT, JPA에서는 JsonNode 객체, Converter가 변환해줌
    @Convert(converter = JsonNodeConverter.class)
    @Column(name="content_json", columnDefinition = "LONGTEXT")
    private JsonNode contentJson;

    // 리포트 생성 시간
    // insert 시에 자동 입력
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 리포트 수정 시간
    // update 시에 자동 갱신
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;


}
