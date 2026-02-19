package com.tradenova.training.repository;

import com.tradenova.training.entity.TrainingRiskRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

//훈련 세션에 설정된 손절/익절 규칙을 DB 에서 가져오기 위한 인터페이스
public interface TrainingRiskRuleRepository extends JpaRepository<TrainingRiskRule, Long> {

    //특정 훈련 세션에 설정된 리스크 규칙 조회
    Optional<TrainingRiskRule> findByChartId(Long ChartId);
    //특정 세션 + 특정 계좌 기준 리스크 규칙 조회
    Optional<TrainingRiskRule> findByChartIdAndAccountId(Long ChartId, Long accountId);

}
