package com.tradenova.training.repository;

import com.tradenova.training.entity.TrainingSessionChart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrainingSessionChartRepository extends JpaRepository<TrainingSessionChart, Long> {

    // chartId + userId(세션 소유자)로 소유권 검증까지 한 번에
    Optional<TrainingSessionChart> findByIdAndSession_User_Id(Long chartId, Long userId);

    List<TrainingSessionChart> findAllBySession_IdOrderByChartIndexAsc(Long sessionId);

    /**
     * 세션 화면에 실제로 보여줄 활성 차트 목록
     * - 새로고침으로 비활성화된 과거 차트는 제외
     */
    List<TrainingSessionChart> findAllBySession_IdAndActiveTrueOrderByChartIndexAsc(Long sessionId);

    /**
     * 특정 세션의 특정 index에 있는 현재 활성 차트 조회
     * - refresh 시 같은 자리의 active 차트를 찾는 용도
     */
    Optional<TrainingSessionChart> findBySession_IdAndChartIndexAndActiveTrue(
            Long sessionId,
            Integer chartIndex
    );

    /**
     * 세션 내 새로고침으로 생성된 차트 개수
     * - 플랜별 refresh 횟수 제한용
     */
    long countBySession_IdAndRefreshedTrue(Long sessionId);

    /**
     * Progress(advance/next)에서 사용할 "정석 조회" 메서드
     * - chartId + userId(세션 소유자) 검증
     * - 동시에 여러 요청이 들어오면 progressIndex가 꼬일 수 있으므로
     *   비관락(PESSIMISTIC_WRITE)으로 row-level lock을 건다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE) // CHANGED
    @Query("""
        select c
        from TrainingSessionChart c
        join c.session s
        join s.user u
        where c.id = :chartId
          and u.id = :userId
    """) // CHANGED
    Optional<TrainingSessionChart> findForUpdateByIdAndUserId(
            @Param("chartId") Long chartId,
            @Param("userId") Long userId
    );
}
