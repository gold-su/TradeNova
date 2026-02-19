package com.tradenova.training.controller;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.training.dto.TradeRequest;
import com.tradenova.training.dto.TradeResponse;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.service.TrainingTradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 훈련(Training) 세션 내 매수/매도 트레이드 API 컨트롤러
 *
 * - 실제 주문이 아닌 "훈련용 가상 거래"를 처리
 * - 현재 진행 중인 TrainingSession에 대해서만 거래 가능
 * - 인증된 사용자(@AuthenticationPrincipal) 기준으로 권한 검증
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training/charts/{chartId}/trades")
public class TrainingTradeController {

    /**
     * 훈련 트레이드 비즈니스 로직 담당 서비스
     */
    private final TrainingTradeService tradeService;

    /**
     * 매수(BUY) 트레이드 실행
     *
     * POST /api/training/charts/{chartId}/trades/buy
     *
     * @param authentication       인증된 토큰
     * @param chartId  훈련 세션 ID
     * @param req        매수 요청 DTO (수량 등)
     * @return           체결 결과(가격, 수량, 잔고 반영 등)
     *
     * 검증/제약 사항 (Service 단에서 처리):
     * - 세션 소유자 == user 인지 확인
     * - 세션 상태가 ONGOING 인지
     * - 매수 수량(qty) > 0
     * - 가용 현금 잔고 충분한지
     */
    @PostMapping("/buy")
    public ResponseEntity<TradeResponse> buy(
            Authentication authentication,
            @PathVariable Long chartId,
            @Valid @RequestBody TradeRequest req
    ) {
        Object p = authentication.getPrincipal();
        Long userId = (p instanceof Long) ? (Long) p : Long.valueOf(p.toString());

        return ResponseEntity.ok(
                tradeService.buy(userId, chartId, req.qty())
        );
    }

    /**
     * 매도(SELL) 트레이드 실행
     *
     * POST /api/training/charts/{chartId}/trades/sell
     *
     * @param authentication      인증된 토큰
     * @param chartId  훈련 세션 ID
     * @param req        매도 요청 DTO (수량 등)
     * @return           체결 결과(가격, 수량, 잔고 반영 등)
     *
     * 검증/제약 사항 (Service 단에서 처리):
     * - 세션 소유자 == user 인지 확인
     * - 세션 상태가 ONGOING 인지
     * - 보유 수량 >= 매도 수량
     */
    @PostMapping("/sell")
    public ResponseEntity<TradeResponse> sell(
            Authentication authentication,
            @PathVariable Long chartId,
            @Valid @RequestBody TradeRequest req
    ) {

        Object p = authentication.getPrincipal();
        Long userId = (p instanceof Long) ? (Long) p : Long.valueOf(p.toString());

        return ResponseEntity.ok(
                tradeService.sell(userId, chartId, req.qty())
        );
    }

    /**
     * 전량 매도 (SELL ALL)
     * POST /api/training/charts/{chartId}/sell-all
     */
    @PostMapping("/sell-all")
    public ResponseEntity<TradeResponse> sellAll(
            Authentication authentication,
            @PathVariable Long chartId
    ) {
        //JWT 인증 정보에서 사용자 ID 추출
        // (Security 설정에서 principal을 Long userId로 넣어둔 전체)
        Object p = authentication.getPrincipal();
        Long userId = (p instanceof Long) ? (Long) p : Long.valueOf(p.toString());

        return ResponseEntity.ok(tradeService.sellAll(userId, chartId));
    }

}
