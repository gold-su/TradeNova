package com.tradenova.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.kis.dto.CandleDto;
import com.tradenova.market.service.MarketDataService;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.report.entity.Type;
import com.tradenova.report.service.TrainingEventService;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.symbol.repository.SymbolRepository;
import com.tradenova.training.dto.*;
import com.tradenova.training.entity.*;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import com.tradenova.user.entity.User;
import com.tradenova.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingSessionService {

    private static final int MIN_BARS = 30;
    private static final int MAX_BARS = 120;

    private static final int MAX_CHARTS = 4;
    private static final int DEFAULT_CHART_COUNT = 4;

    private static final int MAX_TRIES_PER_CHART = 15;

    private final ObjectMapper objectMapper;

    private static final int DEFAULT_INITIAL_VISIBLE_BARS = 60;

    // 세션 저장/조회
    private final TrainingSessionRepository sessionRepo;
    // 차트 저장/조회 (실제 훈련 단위: 종목/기간/bars/progressIndex 등 보유)
    private final TrainingSessionChartRepository chartRepo;
    // 중목 후보군 조회(활성 종목)
    private final SymbolRepository symbolRepository;
    // userId -> User 엔티티 조회(안전하게 영속 상태 확보)
    private final UserRepository userRepository;
    // 캐시 우선 시장 데이터 조회 서비스
    private final MarketDataService marketDataService;
    private final PaperAccountRepository paperAccountRepository;
    // 캔들 저장/조회용 Repository
    private final TrainingSessionCandleRepository candleRepo;
    // 이벤트 서비스
    private final TrainingEventService trainingEventService;

    /**
     * 세션 생성 (RANDOM)
     */
    @Transactional
    public TrainingSessionCreateResponse createSession(Long userId, TrainingSessionCreateRequest req){

        // 0) validation 함수로 검증
        validateCreateRequest(req);

        int bars = req.bars();

        // CHANGED: chartCount 지원 (없으면 기본 4)
        int chartCount = resolveChartCount(req);

        // active symbol 후보군 가져오기
        // - 훈련에 쓸 수 있는 종목 풀(활성화=true)만 가져옴
        List<Symbol> candidates = symbolRepository.findAllByActiveTrueOrderByIdAsc();
        if(candidates.isEmpty()){
            //종목이 0개면 랜덤 뽑기 자체가 불가능
            throw new CustomException(ErrorCode.SYMBOL_NOT_FOUND);
        }

        // 중복 없이 chartCount 개수를 만들 수 있는지 확인
        if (candidates.size() < chartCount) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_CREATE_FAILED);
        }

        // 1) 유저 조회(혹시나 principal user가 detached인 경우 대비)
        // - auth principal 에서 받은 user가 dateched일 수도 있고(영속성 컨텍스트 밖)
        // - DB에 실제 존재하는 유저인지 검증도 해야 함
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 2) account 소유권 체크
        // - userId가 가진 계좌인지 확인 (다른 유저 계좌로 훈련 못 하게 보안)
        PaperAccount account = paperAccountRepository.findByIdAndUserId(req.accountId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAPER_ACCOUNT_NOT_FOUND));

        // 4) 세션은 무조건 "한 번만" 생성
        TrainingSession session = sessionRepo.save(
                TrainingSession.builder()
                        .user(user)
                        .account(account)
                        .mode(req.mode())
                        .status(TrainingStatus.IN_PROGRESS)
                        .build()
        );

        //  5) 차트를 chartCount 만큼 생성
        List<TrainingChartCreateResponse> chartResponses = new ArrayList<>(chartCount);
        // 중복 방지 set
        Set<Long> usedSymbolIds = new HashSet<>();

        for (int chartIndex = 0; chartIndex < chartCount; chartIndex++) {
            TrainingChartCreateResponse chartRes = createOneRandomChartWithCandles(
                    session,
                    candidates,
                    bars,
                    chartIndex,
                    usedSymbolIds
            );
            chartResponses.add(chartRes);
        }

        // 6) 응답은 chart[] 포함 정석 DTO로
        return new TrainingSessionCreateResponse(
                session.getId(),
                account.getId(),
                session.getMode(),
                session.getStatus(),
                chartResponses
        );
    }

    /**
     * 세션 기반 캔들 조회
     * - symbol/from/to를 클라이언트가 조작 못하게 "세션에서"만 가져온다.
     * - 즉, 프론트는 sessionId만 주고 서버가 세션 정보로 캔들 범위를 확정한다.
     */
    @Transactional(readOnly = true)
    public List<CandleDto> getChartCandles(Long userId, Long chartId){

        // 1) 차트 조회
        TrainingSessionChart chart = chartRepo.findByIdAndSession_User_Id(chartId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        /**
        // 2) 세션에 저장된 날짜 범위를 KIS 요청 형식(YYYYMMDD)로 변환
        String from = session.getStartDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String to = session.getEndDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            DB 조회로 변경
         */

        // 2) DB에서 캔들 조회
        List<TrainingSessionCandle> rows = candleRepo.findAllByChartIdOrderByIdxAsc(chart.getId());

        // 비어있으면 예외
        if (rows.isEmpty()) {
            throw new CustomException(ErrorCode.CANDLES_EMPTY);
        }

        // 3) 엔티티 -> CandleDto 변환
        return rows.stream()
                .map(r -> new CandleDto(
                        r.getT(),
                        r.getO(),
                        r.getH(),
                        r.getL(),
                        r.getC(),
                        r.getV()
                ))
                .toList();
    }

    /**
     * 세션 디테일 가져오기
     */
    @Transactional(readOnly = true)
    public SessionDetailResponse getSession(Long userId, Long sessionId) {

        TrainingSession s = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        return new SessionDetailResponse(
                s.getId(),
                s.getAccount().getId(),
                s.getMode(),
                s.getStatus()
        );
    }

    /**
     * 세션의 차트 목록 가져오기
     */
    @Transactional(readOnly = true)
    public List<ChartSummaryResponse> getSessionCharts(Long userId, Long sessionId) {
        TrainingSession s = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        List<TrainingSessionChart> charts = chartRepo.findAllBySession_IdOrderByChartIndexAsc(s.getId());

        return charts.stream()
                .map(c -> new ChartSummaryResponse(
                        c.getId(),
                        c.getChartIndex(),
                        c.getSymbol().getId(),
                        c.getSymbol().getTicker(),
                        c.getSymbol().getName(),
                        c.getBars(),
                        c.getProgressIndex(),
                        c.getStatus(),
                        c.getStartDate(),
                        c.getEndDate()
                ))
                .toList();
    }


    //=================================================
    //validate 분리
    private void validateCreateRequest(TrainingSessionCreateRequest request) throws CustomException {
        if(request == null) throw new CustomException(ErrorCode.INVALID_REQUEST);

        if(request.mode() == null) throw new CustomException(ErrorCode.INVALID_TRAINING_MODE);

        if(request.accountId() == null) throw new CustomException(ErrorCode.INVALID_REQUEST);

        if (request.bars() == null || request.bars() < MIN_BARS || request.bars() > MAX_BARS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // chartCount가 요청 DTO에 없으면 이 부분 컴파일 에러 날 수 있음
        // 그 경우 resolveChartCount()에서 request.chartCount() 관련 코드만 삭제하면 됨.
        if(request.chartCount() != null){
            int c = request.chartCount();
            if(c<1 || c>MAX_CHARTS) throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    private int resolveChartCount(TrainingSessionCreateRequest request){
        // CHANGED: chartCount 지원(없으면 기본값)
        Integer count = request.chartCount();
        return (count == null) ? DEFAULT_CHART_COUNT : count;
    }


    /**
     *  핵심: 차트 1개를 만들고 + 캔들 저장까지 끝내고 + 차트 DTO 리턴
     * - 실패하면 MAX_TRIES_PER_CHART 만큼 재시도
     * - 결국 실패하면 예외 → 트랜잭션 롤백
     */
    private TrainingChartCreateResponse createOneRandomChartWithCandles(
            TrainingSession session,
            List<Symbol> candidates,
            int bars,
            int chartIndex,
            Set<Long> usedSymbolIds
    ) {
        for (int attempt = 1; attempt <= MAX_TRIES_PER_CHART; attempt++) {

            Symbol picked = pickRandom(candidates);

            // 이미 이 세션에서 사용한 종목이면 다시 뽑기
            if (usedSymbolIds.contains(picked.getId())) {
                continue;
            }

            //  랜덤 기간 생성
            LocalDate endDate = randomDate(LocalDate.of(2018, 1, 1), LocalDate.now().minusDays(30));
            LocalDate startDate = endDate.minusDays(bars * 5L);

            //  DB 캔들 조회
            List<CandleDto> candles = new ArrayList<>(
                    marketDataService.getCandles(picked, startDate, endDate, bars)
            );

            if (candles.size() < bars) continue;

            candles.sort(java.util.Comparator.comparingLong(CandleDto::t));

            //  봉 기준으로 최종 구간 확정
            int fromIndex = candles.size() - bars;
            List<CandleDto> sessionCandles = new ArrayList<>(candles.subList(fromIndex, candles.size()));

            LocalDate finalStart = millisToSeoulDate(sessionCandles.get(0).t());
            LocalDate finalEnd = millisToSeoulDate(sessionCandles.get(sessionCandles.size() - 1).t());

            //  초기 공개 progressIndex 계산
            int initialVisibleBars = Math.min(DEFAULT_INITIAL_VISIBLE_BARS, bars);
            int progressIndex = Math.max(0, initialVisibleBars - 1);

            //  차트 생성
            TrainingSessionChart chart = chartRepo.save(
                    TrainingSessionChart.builder()
                            .session(session)
                            .chartIndex(chartIndex)
                            .symbol(picked)
                            .startDate(finalStart)
                            .endDate(finalEnd)
                            .bars(bars)
                            .hiddenFutureBars(0)
                            .progressIndex(progressIndex)
                            .status(TrainingChartStatus.IN_PROGRESS)
                            .build()
            );

            //  캔들 저장 (chartId 기반, idx=0..bars-1)
            List<TrainingSessionCandle> entities = new ArrayList<>(sessionCandles.size());
            for (int i = 0; i < sessionCandles.size(); i++) {
                CandleDto c = sessionCandles.get(i);
                entities.add(
                        TrainingSessionCandle.builder()
                                .chartId(chart.getId())
                                .idx(i)
                                .t(c.t())
                                .o(c.o())
                                .h(c.h())
                                .l(c.l())
                                .c(c.c())
                                .v(c.v())
                                .build()
                );
            }
            // 나중에 rewriteBatchedStatements=true 옵션 키기 Insert 1번씩 나가는거 없애기.
            candleRepo.saveAll(entities);

            usedSymbolIds.add(picked.getId());

            // 응답용 DTO
            return new TrainingChartCreateResponse(
                    chart.getId(),
                    chart.getChartIndex(),
                    picked.getId(),
                    picked.getTicker(),
                    picked.getName(),
                    chart.getBars(),
                    chart.getProgressIndex(),
                    chart.getStatus(),
                    chart.getStartDate(),
                    chart.getEndDate()
            );
        }
        // 차트 1개를 끝내 못 만들면 세션 생성 자체 실패로 처리 (트랜잭션 롤백)
        throw new CustomException(ErrorCode.TRAINING_SESSION_CREATE_FAILED);
    }

    //session 종료 메서드
    @Transactional
    public SessionFinishResponse finishSession(Long userId, Long sessionId) {
        // 1. 세션 조회 + 권한 체크
        TrainingSession session = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        // 이미 종료한 세션 에러
        if (session.getStatus() == TrainingStatus.COMPLETED) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_ALREADY_COMPLETED);
        }

        // 2. 차트 전부 가져오기
        List<TrainingSessionChart> charts = chartRepo.findAllBySession_IdOrderByChartIndexAsc(session.getId());

        // 3. 카운터 변수
        int completedBefore = 0; // 이미 끝난 차트
        int forceCompleted = 0; // 강제로 끝낸 차트

        // 4. 차트 돌면서 처리
        for (TrainingSessionChart chart : charts) {
            if (chart.getStatus() == TrainingChartStatus.COMPLETED) {
                completedBefore++;
                continue;
            }

            // 강제 종료
            chart.complete();
            forceCompleted++;

            // 차트 강제 종료 이벤트 저장
            ObjectNode chartPayload = objectMapper.createObjectNode();
            chartPayload.put("sessionId", session.getId());
            chartPayload.put("chartId", chart.getId());
            chartPayload.put("chartIndex", chart.getChartIndex());
            chartPayload.put("symbolId", chart.getSymbol().getId());
            chartPayload.put("symbolTicker", chart.getSymbol().getTicker());
            chartPayload.put("symbolName", chart.getSymbol().getName());
            chartPayload.put("reason", "FORCED_SESSION_FINISH");

            trainingEventService.append(
                    userId,
                    chart.getId(),
                    Type.NOTE,
                    "차트 강제 종료",
                    chartPayload
            );
        }

        // 5. 세션 종료
        session.setStatus(TrainingStatus.COMPLETED);

        // 6. 세션 종료 이벤트 저장
        ObjectNode sessionPayload = objectMapper.createObjectNode();
        sessionPayload.put("sessionId", session.getId());
        sessionPayload.put("totalCharts", charts.size());
        sessionPayload.put("completedBefore", completedBefore);
        sessionPayload.put("forceCompleted", forceCompleted);
        sessionPayload.put("finalCompletedCount", completedBefore + forceCompleted);

        // chartId 없는 세션 이벤트는 첫 차트 기준으로 남기거나
        // 정책상 chartId nullable 허용이 아니면 첫 차트 id를 대표로 사용
        Long representativeChartId = charts.isEmpty() ? null : charts.get(0).getId();

        if (representativeChartId != null) {
            trainingEventService.append(
                    userId,
                    representativeChartId,
                    Type.NOTE,
                    "세션 종료",
                    sessionPayload
            );
        }

        // 8. 결과 반환
        return new SessionFinishResponse(
                session.getId(),
                session.getStatus().name(),
                charts.size(),
                completedBefore + forceCompleted,
                forceCompleted
        );
    }

    /**
     * 현재 사용자의 "진행 중(IN_PROGRESS)"인 가장 최근 세션을 조회한다.
     * - 사용자가 페이지를 새로고침하거나 재접속했을 때 이어서 진행할 수 있는 세션 상태로 복구.
     */
    @Transactional(readOnly = true)
    public ActiveTrainingSessionResponse getActiveSession(Long userId) {

        // 1) 가장 최근 진행 중 세션 조회 (없으면 null)
        TrainingSession session = sessionRepo
                .findTopByUserIdAndStatusOrderByIdDesc(userId, TrainingStatus.IN_PROGRESS)
                .orElse(null);
        // 진행 중 세션이 없으면 그대로 null 반환
        if (session == null) {
            return null;
        }
        // 2) 해당 세션의 차트 목록 조회 (chartIndex 순서 유지)
        List<TrainingSessionChart> charts = chartRepo.findAllBySession_IdOrderByChartIndexAsc(session.getId());
        // 3) 엔티티 -> DTO 변환
        List<ChartSummaryResponse> chartDtos = charts.stream()
                .map(c -> new ChartSummaryResponse(
                        c.getId(),                  // chartId
                        c.getChartIndex(),          // 차트 위치 (0~3)
                        c.getSymbol().getId(),      // 종목 ID
                        c.getSymbol().getTicker(),  // 종목 코드
                        c.getSymbol().getName(),    // 종목 이름
                        c.getBars(),                // 총 봉 개수
                        c.getProgressIndex(),       // 현재 진행 위치
                        c.getStatus(),              // 차트 상태
                        c.getStartDate(),           // 시작일
                        c.getEndDate()              // 종료일
                ))
                .toList();
        // 4) 완료된 차트 개수 계산
        int completedCount = (int) charts.stream()
                .filter(c -> c.getStatus() == TrainingChartStatus.COMPLETED)
                .count();
        // 5) 최종 응답 반환
        return new ActiveTrainingSessionResponse(
                session.getId(),                // 세션 ID
                session.getAccount().getId(),   // 계좌 ID
                session.getMode(),              // 훈련 모드
                session.getStatus(),            // 세션 상태
                charts.size(),                  // 전체 차트 수
                completedCount,                 // 완료된 차트 수
                chartDtos                       // 차트 리스트
        );
    }

    @Transactional
    public TrainingChartCreateResponse refreshChart(Long userId, Long chartId, ChartRefreshRequest req) {

        // null 예외
        if (req == null || req.refreshType() == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // 1) 기존 차트 조회 + 소유권 체크
        TrainingSessionChart currentChart = chartRepo.findByIdAndSession_User_Id(chartId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        TrainingSession session = currentChart.getSession();

        // 2) 같은 세션의 차트 목록 조회
        List<TrainingSessionChart> charts = chartRepo.findAllBySession_IdOrderByChartIndexAsc(session.getId());

        // 3) 현재 차트를 제외한 다른 차트들의 종목 ID 수집
        Set<Long> usedSymbolIds = charts.stream()
                .filter(c -> !c.getId().equals(currentChart.getId()))
                .map(c -> c.getSymbol().getId())
                .collect(java.util.stream.Collectors.toSet());

        // 4) 새로고침 조건에 맞는 후보 종목 조회
        List<Symbol> candidates = resolveRefreshCandidates(req);

        if (candidates.isEmpty()) {
            throw new CustomException(ErrorCode.SYMBOL_NOT_FOUND);
        }

        // 5) 기존 차트는 삭제하지 않고 COMPLETED 처리 or 그대로 둘 수도 있음
        // 지금은 데이터 혼선 방지를 위해 완료 처리
        currentChart.complete();

        // 6) 새 차트 생성 (기존 chartIndex 유지)
        return createOneRandomChartWithCandles(
                session,
                candidates,
                currentChart.getBars(),
                currentChart.getChartIndex(),
                usedSymbolIds
        );
    }

    // ===== helpers =====

    // 후보 리스트에서 랜덤 1개 선택
    private static Symbol pickRandom(List<Symbol> list) {
        int idx = ThreadLocalRandom.current().nextInt(list.size());
        return list.get(idx);
    }

    // from~to 사이 날짜를 랜덤으로 뽑기 (LocalDate)
    private static LocalDate randomDate(LocalDate from, LocalDate to) {
        long fromEpoch = from.toEpochDay();
        long toEpoch = to.toEpochDay();
        long rand = ThreadLocalRandom.current().nextLong(fromEpoch, toEpoch + 1);
        return LocalDate.ofEpochDay(rand);
    }

    // epochMillis(캔들 시간) → Asia/Seoul 기준 LocalDate로 변환
    private static LocalDate millisToSeoulDate(long epochMillis) {
        return java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneId.of("Asia/Seoul"))
                .toLocalDate();
    }

    /**
     * 새로고침 요청 조건에 따라 후보 종목 목록을 만든다.
     *
     * 현재 지원:
     * - RANDOM
     * - TRAINING_SECTOR
     * - EXCHANGE_SECTOR
     *
     * 향후:
     * - TOP_VOLUME
     * - ORDER_FLOW
     * - THEME
     */
    private List<Symbol> resolveRefreshCandidates(ChartRefreshRequest req) {

        // switch expression 사용 (값을 반환하는 switch)
        return switch (req.refreshType()) {

            // 1. RANDOM: 모든 활성 종목 반환
            case RANDOM -> symbolRepository.findAllByActiveTrueOrderByIdAsc();

            // 2. TRAINING_SECTOR: 내부 정의된 섹터 기준 필터
            case TRAINING_SECTOR -> {

                // optionValue가 없으면 잘못된 요청
                if (req.optionValue() == null || req.optionValue().isBlank()) {
                    throw new CustomException(ErrorCode.INVALID_REQUEST);
                }

                // 문자열 -> Enum 반환
                com.tradenova.symbol.dto.SymbolSector sector;
                try {
                    sector = com.tradenova.symbol.dto.SymbolSector.valueOf(req.optionValue());
                } catch (IllegalArgumentException e) {
                    // enum에 없는 값이면 예외
                    throw new CustomException(ErrorCode.INVALID_REQUEST);
                }

                // 해당 섹터에 속한 활성 종목 조회
                yield symbolRepository.findAllByActiveTrueAndTrainingSectorOrderByIdAsc(sector);
            }

            // 3. EXCHANGE_SECTOR: 거래소 섹터 기준 필터
            case EXCHANGE_SECTOR -> {

                // optionValue 필수
                if (req.optionValue() == null || req.optionValue().isBlank()) {
                    throw new CustomException(ErrorCode.INVALID_REQUEST);
                }

                // 문자열 그대로 사용해서 조회
                yield symbolRepository.findAllByActiveTrueAndExchangeSectorOrderByIdAsc(req.optionValue());
            }

            // 4. 아직 구현 안 한 확장 타입들
            case TOP_VOLUME, ORDER_FLOW, THEME -> throw new CustomException(ErrorCode.INVALID_REQUEST);
        };
    }

}
