package com.tradenova.report.entity;


import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.common.jpa.JsonNodeConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name="training_event",
        indexes = {
                // 특정 사용자 + 특정 차트의 이벤트 히스토리
                @Index(name="idx_event_user_chart_time", columnList="user_id, chart_id, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// 훈련 중 발생하는 모든 행동의 로그 저장소
public class TrainingEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //이벤트 소유자
    @Column(name="user_id", nullable=false)
    private Long userId;

    //어떤 차트에서 발생한 이벤트인지
    @Column(name="chart_id", nullable=false)
    private Long chartId;

    /**
     * 이벤트 종류
     * PROGRESS
     * TRADE
     * WARNING
     * AI
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private Type type;

    // UI 한 줄 로그(사람이 읽는 문자열) - 화면에 바로 띄우기
    /**
     * "10봉 진행"
     * "삼성전자 3주 매수"
     * "손절 라인 미입력 경고"
     * "AI: 감정적 매수 경향"
     */
    @Column(nullable=false, length=200)
    private String summary;

    // 확장 핵심: 이벤트별 상세는 JSON
    // 예: PROGRESS {steps, from, to, price}
    /**
     * {
     *   "steps": 10,
     *   "from": 59,
     *   "to": 69,
     *   "price": 73500
     * }
     */
    // 예: TRADE {side, qty, executedPrice, cash, positionQty, avgPrice}
    /**
     * {
     *   "side": "BUY",
     *   "qty": 3,
     *   "executedPrice": 73000,
     *   "cash": 9300000,
     *   "positionQty": 5,
     *   "avgPrice": 71000
     * }
     */
    // 예: WARNING {code, message, severity}
    /**
     * {
     *   "code": "NO_STOP_LOSS",
     *   "message": "손절가 미입력",
     *   "severity": "HIGH"
     * }
     */
    // 예: AI {oneLine, habits[], score}
    /**
     * {
     *   "oneLine": "이유 없는 매수 경향",
     *   "score": 62,
     *   "habits": ["추격매수", "손절 지연"]
     * }
     */
    // JsonNode == Jackson 라이브러리에서 제공하는 "JSON을 트리 구조로 다루는 객체"
    @Convert(converter = JsonNodeConverter.class)
    @Column(name="payload_json", columnDefinition="LONGTEXT")
    private JsonNode payloadJson;

    //이벤트 생성 시 자동 기록
    @CreationTimestamp
    @Column(name="created_at", updatable=false)
    private Instant createdAt;
}
