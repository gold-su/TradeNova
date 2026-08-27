package com.tradenova.training.repository;

import com.tradenova.training.entity.TrainingRiskRuleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TrainingRiskRuleHistoryRepository
        extends JpaRepository<TrainingRiskRuleHistory, Long> {

    List<TrainingRiskRuleHistory> findAllByChartIdOrderByIdAsc(Long chartId);

    Optional<TrainingRiskRuleHistory> findTopByChartIdOrderByIdDesc(Long chartId);

    /** Resolve all episode risk evidence in one IN query. */
    List<TrainingRiskRuleHistory> findAllByIdIn(Collection<Long> ids);
}
