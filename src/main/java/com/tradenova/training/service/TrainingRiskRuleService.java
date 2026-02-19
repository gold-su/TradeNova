package com.tradenova.training.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.kis.service.KisMarketDataService;
import com.tradenova.kis.dto.CandleDto;
import com.tradenova.kis.util.KisMarketCodeMapper;
import com.tradenova.training.dto.RiskRuleResponse;
import com.tradenova.training.dto.RiskRuleUpsertRequest;
import com.tradenova.training.entity.*;
import com.tradenova.training.repository.TrainingRiskRuleRepository;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainingRiskRuleService {

    // 차트 소유권 검증용
    private final TrainingSessionChartRepository chartRepo;
    //리스크 룰 조회/저장
    private final TrainingRiskRuleRepository riskRepo;
    private final TrainingSessionCandleRepository candleRepo;

    /**
     * 리스크 룰 조회
     * - 룰이 없으면 "미설정" 기본 응답 반환 (프론트 UX 편함)
     */
    @Transactional(readOnly = true)
    public RiskRuleResponse get(Long userId, Long chartId) {

        // 1) 차트 조회 + 소유권 검증 (내 차트만 접근)
        TrainingSessionChart chart = chartRepo.findByIdAndSession_User_Id(chartId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        // 2) 계좌 ID
        Long accountId = chart.getSession().getAccount().getId();

        // 3) 룰 조회 (차트 기준)
        return riskRepo.findByChartId(chartId)
                .map(this::toResponse)
                .orElse(new RiskRuleResponse(
                        null,       // ruleId
                        chartId,    // chartId (DTO에 chartId 필드가 있어야 함)
                        accountId,  // accountId
                        null,       // stopLossPrice
                        null,       // takeProfitPrice
                        false,      // enabled
                        null        // updatedAt
                ));
    }

    /**
     * 리스크 룰 생성/수정 (Upsert)
     * - 있으면 update
     * - 없으면 insert
     */
    @Transactional
    public RiskRuleResponse upsert(Long userId, Long chartId, RiskRuleUpsertRequest req) {

        // 1) 차트 조회 + 소유권 검증
        TrainingSessionChart chart = chartRepo.findByIdAndSession_User_Id(chartId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        // 2) 세션 상태 검증 (진행 중만 설정 가능)
        if (chart.getSession().getStatus() != TrainingStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
        }

        Long accountId = chart.getSession().getAccount().getId();

        // 3) enabled=true인데 손절/익절 둘 다 null이면 의미 없음
        if (Boolean.TRUE.equals(req.autoExitEnabled())
                && req.stopLossPrice() == null
                && req.takeProfitPrice() == null) {
            throw new CustomException(ErrorCode.RISK_RULE_EMPTY_WHEN_ENABLED);
        }

        // 4) 현재가 계산 (차트 기준)
        BigDecimal currentPrice = getCurrentPrice(chart);

        // 5) 손절/익절 가격 검증(정책)
        // 손절가는 현재가보다 "낮아야" 정상
        if (req.stopLossPrice() != null && req.stopLossPrice().compareTo(currentPrice) >= 0) {
            throw new CustomException(ErrorCode.INVALID_STOP_LOSS_PRICE);
        }
        // 익절가는 현재가보다 "높아야" 정상
        if (req.takeProfitPrice() != null && req.takeProfitPrice().compareTo(currentPrice) <= 0) {
            throw new CustomException(ErrorCode.INVALID_TAKE_PROFIT_PRICE);
        }

        // 6) 기존 룰 있으면 가져오고, 없으면 새로 생성
        TrainingRiskRule rule = riskRepo.findByChartId(chartId)
                .orElseGet(() -> TrainingRiskRule.builder()
                        .chartId(chartId)
                        .accountId(accountId)
                        .enabled(false)
                        .build()
                );

        // 7) 값 반영
        rule.setStopLossPrice(req.stopLossPrice());
        rule.setTakeProfitPrice(req.takeProfitPrice());

        if (req.autoExitEnabled() != null) {
            rule.setEnabled(req.autoExitEnabled());
        }

        // 8) 저장
        TrainingRiskRule saved = riskRepo.save(rule);

        // 9) 응답 반환
        return toResponse(saved);
    }

    /**
     * 차트 progressIndex 기준 "안전한 현재가(close)" 구하기
     */
    private BigDecimal getCurrentPrice(TrainingSessionChart chart) {
        int idx = (chart.getProgressIndex() == null) ? 0 : chart.getProgressIndex();
        int maxIdx = Math.max(0, chart.getBars() - 1);
        idx = Math.max(0, Math.min(idx, maxIdx));

        TrainingSessionCandle candle = candleRepo.findByChartIdAndIdx(chart.getId(), idx)
                .orElseThrow(() -> new CustomException(ErrorCode.CANDLES_EMPTY));

        return BigDecimal.valueOf(candle.getC());
    }

    /**
     * 엔티티 -> 응답 DTO 변환
     */
    private RiskRuleResponse toResponse(TrainingRiskRule r) {
        return new RiskRuleResponse(
                r.getId(),
                r.getChartId(),
                r.getAccountId(),
                r.getStopLossPrice(),
                r.getTakeProfitPrice(),
                r.isEnabled(),
                r.getUpdatedAt()
        );
    }
}
