package com.tradenova.training.repository;


import com.tradenova.training.entity.TrainingSessionCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingSessionCandleRepository extends JpaRepository<TrainingSessionCandle, Long> {

    /**
     * 특정 차트의 전체 캔들을 idx 오름차순으로 조회
     */
    List<TrainingSessionCandle> findAllByChartIdOrderByIdxAsc(Long ChartId);

    /**
     * 특정 차트의 현재 progressIndex에 해당하는 캔들 조회
     *
     * 새로고침 후 currentPrice 복구에 사용한다.
     */
    Optional<TrainingSessionCandle> findByChartIdAndIdx(Long ChartId, Integer idx);

    /** visibleOnly 옵션 (치팅 방지 강화용) */
    List<TrainingSessionCandle> findAllByChartIdAndIdxLessThanEqualOrderByIdxAsc( //LessThen = 비교연산
            Long ChartId,
            Integer idx
    );

    /** 세션 삭제/리셋 시 사용 */
    void deleteAllByChartId(Long ChartId);

    /**
     * 특정 차트의 최근 30개 봉 조회
     * - AI 분석용
     */
    List<TrainingSessionCandle> findTop30ByChartIdOrderByIdxDesc(Long chartId);

}
