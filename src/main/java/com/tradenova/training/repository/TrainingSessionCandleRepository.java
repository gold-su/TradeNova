package com.tradenova.training.repository;


import com.tradenova.training.entity.TrainingSessionCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingSessionCandleRepository extends JpaRepository<TrainingSessionCandle, Long> {

    /** 세션 전체 캔들 (차트 초기 로딩용) */
    List<TrainingSessionCandle> findAllByChartIdOrderByIdxAsc(Long ChartId);

    /** progressIndex 기준 현재 봉 (currentPrice 계산) */
    Optional<TrainingSessionCandle> findByChartIdAndIdx(Long ChartId, Integer idx);

    /** visibleOnly 옵션 (치팅 방지 강화용) */
    List<TrainingSessionCandle> findAllByChartIdAndIdxLessThanEqualOrderByIdxAsc( //LessThen = 비교연산
            Long ChartId,
            Integer idx
    );

    /** 세션 삭제/리셋 시 사용 */
    void deleteAllByChartId(Long ChartId);

}
