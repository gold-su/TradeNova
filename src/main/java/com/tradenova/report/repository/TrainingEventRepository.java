package com.tradenova.report.repository;

import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
