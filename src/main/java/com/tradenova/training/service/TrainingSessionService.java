package com.tradenova.training.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.kis.service.KisMarketDataService;
import com.tradenova.kis.dto.CandleDto;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.symbol.repository.SymbolRepository;
import com.tradenova.training.dto.*;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import com.tradenova.user.entity.User;
import com.tradenova.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class TrainingSessionService {

    private static final int MIN_BARS = 30;
    private static final int MAX_BARS = 120;

    private static final int MAX_CHARTS = 4;
    private static final int DEFAULT_CHART_COUNT = 4;

    private static final int MAX_TRIES_PER_CHART = 15;

    private static final String MARKET_CODE = "J";
    private static final String PERIOD = "D";
    private static final String ADJ_PRICE = "0";

    private static final DateTimeFormatter KIS_DATE = DateTimeFormatter.BASIC_ISO_DATE;


    // 세션 저장/조회
    private final TrainingSessionRepository sessionRepo;
    // 차트 저장/조회 (실제 훈련 단위: 종목/기간/bars/progressIndex 등 보유)
    private final TrainingSessionChartRepository chartRepo;
    // 중목 후보군 조회(활성 종목)
    private final SymbolRepository symbolRepository;
    // userId -> User 엔티티 조회(안전하게 영속 상태 확보)
    private final UserRepository userRepository;
    // KIS 시세/캔들 조회 서비스
    private final KisMarketDataService kisMarketDataService;
    private final PaperAccountRepository paperAccountRepository;
    // 캔들 저장/조회용 Repository
    private final TrainingSessionCandleRepository candleRepo;

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

        // 1) 유저 조회(혹시나 principal user가 detached인 경우 대비)
        // - auth principal 에서 받은 user가 dateched일 수도 있고(영속성 컨텍스트 밖)
        // - DB에 실제 존재하는 유저인지 검증도 해야 함
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 2) account 소유권 체크
        // - userId가 가진 계좌인지 확인 (다른 유저 계좌로 훈련 못 하게 보안)
        PaperAccount account = paperAccountRepository.findByIdAndUserId(req.accountId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAPER_ACCOUNT_NOT_FOUND));

        // 3) active symbol 후보군 가져오기
        // - 훈련에 쓸 수 있는 종목 풀(활성화=true)만 가져옴
        List<Symbol> candidates = symbolRepository.findAllByActiveTrueOrderByIdAsc();
        if(candidates.isEmpty()){
            //종목이 0개면 랜덤 뽑기 자체가 불가능
            throw new CustomException(ErrorCode.SYMBOL_NOT_FOUND);
        }

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

        for(int chartIndex = 0; chartIndex < chartCount; chartIndex++){
            TrainingChartCreateResponse chartRes = createOneRandomChartWithCandles(
                    session,
                    candidates,
                    bars,
                    chartIndex
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

    private int resolveChartCount(TrainingSessionCreateRequest request) throws CustomException {
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
            int chartIndex
    ) {
        for (int attempt = 1; attempt <= MAX_TRIES_PER_CHART; attempt++) {

            Symbol picked = pickRandom(candidates);

            //  랜덤 기간 생성
            LocalDate endDate = randomDate(LocalDate.of(2018, 1, 1), LocalDate.now().minusDays(30));
            LocalDate startDate = endDate.minusDays(bars * 3L);

            //  KIS 캔들 조회
            List<CandleDto> candles = new ArrayList<>(kisMarketDataService.getCandles(
                    MARKET_CODE,
                    picked.getTicker(),
                    startDate.format(KIS_DATE),
                    endDate.format(KIS_DATE),
                    PERIOD,
                    ADJ_PRICE
            ));

            if (candles.size() < bars) continue;

            candles.sort(java.util.Comparator.comparingLong(CandleDto::t));

            //  봉 기준으로 최종 구간 확정
            int fromIndex = candles.size() - bars;
            List<CandleDto> sessionCandles = new ArrayList<>(candles.subList(fromIndex, candles.size()));

            LocalDate finalStart = millisToSeoulDate(sessionCandles.get(0).t());
            LocalDate finalEnd = millisToSeoulDate(sessionCandles.get(sessionCandles.size() - 1).t());

            //  초기 공개 progressIndex 계산
            int initialVisibleBars = Math.min(60, bars);
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
            candleRepo.saveAll(entities);

            // 응답용 DTO
            return new TrainingChartCreateResponse(
                    chart.getId(),
                    chart.getChartIndex(),
                    picked.getId(),
                    picked.getTicker(),
                    picked.getName(),
                    chart.getBars(),
                    chart.getProgressIndex(),
                    chart.getStartDate(),
                    chart.getEndDate()
            );
        }
        // 차트 1개를 끝내 못 만들면 세션 생성 자체 실패로 처리 (트랜잭션 롤백)
        throw new CustomException(ErrorCode.TRAINING_SESSION_CREATE_FAILED);
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

}
