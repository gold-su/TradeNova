package com.tradenova.training.repository;

import com.tradenova.training.entity.TrainingRiskRuleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainingRiskRuleHistoryRepository
        extends JpaRepository<TrainingRiskRuleHistory, Long> {

    List<TrainingRiskRuleHistory> findAllByChartIdOrderByIdAsc(Long chartId);
}
