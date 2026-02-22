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
