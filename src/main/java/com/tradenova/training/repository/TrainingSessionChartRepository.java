package com.tradenova.training.repository;

import com.tradenova.training.entity.TrainingSessionChart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingSessionChartRepository extends JpaRepository<TrainingSessionChart, Long> {

    // chartId + userId(세션 소유자)로 소유권 검증까지 한 번에
    Optional<TrainingSessionChart> findByIdAndSession_User_Id(Long chartId, Long userId);

    List<TrainingSessionChart> findAllBySession_IdOrderByChartIndexAsc(Long sessionId);
}
