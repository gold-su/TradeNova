package com.tradenova.report.repository;

import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrainingEventRepository extends JpaRepository<TrainingEvent, Long> {

    // 차트별 이벤트 타임라인(최신순)
    List<TrainingEvent> findAllByUserIdAndChartIdOrderByIdDesc(Long userId, Long chartId);

    // 페이징(무한스크롤/최근 N개)
    List<TrainingEvent> findAllByUserIdAndChartIdOrderByIdDesc(Long userId, Long chartId, Pageable pageable);

    // 특정 타입만 필터링(예: WARNING만)
    List<TrainingEvent> findAllByUserIdAndChartIdAndTypeOrderByIdDesc(Long userId, Long chartId, Type type);

    // 유저 소유권 검증 단건
    Optional<TrainingEvent> findByIdAndUserId(Long id, Long userId);

    // 기간 필터링이 필요할 때
    List<TrainingEvent> findAllByUserIdAndChartIdAndCreatedAtBetweenOrderByIdDesc(
            Long userId,
            Long chartId,
            Instant from,
            Instant to
    );

    List<TrainingEvent> findAllByUserIdAndChartIdInAndTypeOrderByIdDesc(
            Long userId,
            List<Long> chartIds,
            Type type
    );


    List<TrainingEvent> findAllByUserIdAndChartIdInOrderByIdAsc(Long userId, List<Long> chartIds);

    /**
     * 특정 차트에 이벤트 기록이 있는지 확인
     * - AI/NOTE/WARNING/TRADE 등 기록 보호용
     */
    boolean existsByUserIdAndChartId(Long userId, Long chartId);

    /**
     * 특정 세션의 가장 최근 SESSION AI 이벤트를 조회한다.
     *
     * TrainingEvent에는 sessionId 전용 컬럼이 없고,
     * payload_json 안에 sessionId가 저장되어 있으므로
     * MySQL JSON_EXTRACT를 사용한다.
     */
    @Query(
            value = """
        SELECT te.*
        FROM training_event te
        WHERE te.user_id = :userId
          AND te.type = 'AI'
          AND JSON_UNQUOTE(
                JSON_EXTRACT(te.payload_json, '$.analysisScope')
              ) = 'SESSION'
          AND CAST(
                JSON_UNQUOTE(
                  JSON_EXTRACT(te.payload_json, '$.sessionId')
                ) AS UNSIGNED
              ) = :sessionId
        ORDER BY te.created_at DESC
        LIMIT 1
        """,
            nativeQuery = true
    )
    Optional<TrainingEvent> findLatestSessionAiEvent(
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId
    );
}
