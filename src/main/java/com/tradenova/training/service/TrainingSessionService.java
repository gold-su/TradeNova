package com.tradenova.training.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.kis.KisMarketDataService;
import com.tradenova.kis.dto.CandleDto;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.paper.service.PaperAccountService;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.symbol.repository.SymbolRepository;
import com.tradenova.training.dto.TrainingSessionCreateRequest;
import com.tradenova.training.dto.TrainingSessionCreateResponse;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.TrainingSessionRepository;
import com.tradenova.user.entity.User;
import com.tradenova.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class TrainingSessionService {

    // 세션 저장/조회
    private final TrainingSessionRepository trainingSessionRepository;
    // 계좌 소유권 검증 및 조회
    private final PaperAccountService paperAccountService;
    //중목 후보군 조회(활성 종목)
    private final SymbolRepository symbolRepository;
    //userId -> User 엔티티 조회(안전하게 영속 상태 확보)
    private final UserRepository userRepository;
    //KIS 시세/캔들 조회 서비스
    private final KisMarketDataService kisMarketDataService;
    private final PaperAccountRepository paperAccountRepository;

    /**
     * 세션 생성 (RANDOM)
     */
    @Transactional
    public TrainingSessionCreateResponse createSession(Long userId, TrainingSessionCreateRequest req){

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
            throw new CustomException(ErrorCode.PAPER_ACCOUNT_NOT_FOUND);
        }

        // 4) 랜덤으로 뽑되, "bars 만큼 캔들이 충분한 종목/기간"을 찾을 때까지 반복
        //    - KIS 데이터는 주말/휴장 때문에 기간을 대충 잡으면 봉 수가 부족할 수 있음
        //    - 그래서 여러 번 시도해서 조건 만족하는 구간을 찾는 방식
        int maxTries = 15;
        for (int attempt =1; attempt <= maxTries; attempt++) {

            // 4-1) 종목 랜덤 선택
            Symbol picked = pickRandom(candidates);

            // 4-2) 랜덤 기간 생성
            // - 너무 최근은 데이터 공백/휴장/이슈로 꼬일 수 있으니 최근 30일 제외
            // (A) 랜덤 기간 생성
            //     - 너무 최근은 데이터 부족/휴장/이벤트 때문에 꼬일 수 있으니 “최근 30일은 제외” 같은 완충을 둠
            //     - 예: 2018~현재-30일 범위에서 랜덤 endDate
            LocalDate endDate = randomDate(LocalDate.of(2018, 1, 1), LocalDate.now().minusDays(30));
            // - bars 봉을 얻기 위해 달력 day를 넉넉히 잡는다(주말/휴장 보정)
            LocalDate startDate = endDate.minusDays(req.bars() * 2L);
            // 위처럼 *2를 하는 이유:
            // - ‘bars=180’이라도 실제로는 주말/공휴일이 빠져서
            //   캘린더 days를 그대로 쓰면 캔들이 부족할 수 있음
            // - 넉넉히 뽑고, 실제로는 KIS 조회 결과로 검증

            // 4-3) KIS로 캔들 조회해서 bars 이상인지 확인
            // - KIS 조회는 YYYYMMDD 문자열을 요구하는 경우가 많아서 BASIC_ISO_DATE 사용
            // (B) KIS로 캔들 조회해서 bars 이상인지 확인
            List<CandleDto> candles = kisMarketDataService.getCandles(
                    "J", //시장코드(예: 국내 KRX)
                    picked.getTicker(), //종목코드(005930 등)
                    startDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE), //from
                    endDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE), //to
                    "D", //일봉
                    "0" //수정주가/조정여부(문서 기준)
            );

            //봉이 부족하면 이 조합은 실패 -> 다음 시도
            if(candles.size() >= req.bars()){
                continue;
            }

            // 4-4) 날짜를 "봉 기준으로 정확히" 다시 맞춘다 (가장 중요)
            // - startDate/endDate를 달력으로 잡으면 휴장으로 인해 실제 봉 수가 달라짐
            // - 그래서 실제 캔들 배열에서 마지막 bars개를 기준으로 최종 구간을 확정
            int fromIndex = Math.max(0, candles.size() - req.bars());

            long startMillis = candles.get(fromIndex).t();   //bars 구간의 첫 봉 시간
            long endMillis = candles.get(candles.size() -1).t(); //마지막 봉 시간

            LocalDate finalStart = millisToSeoulDate(startMillis);
            LocalDate finalEnd = millisToSeoulDate(endMillis);

            // 4-5) 훈련 세션 저장
            // - user/account/symbol + 구간(start/end) + 상태(status) 등을 DB에 기록
            TrainingSession saved = trainingSessionRepository.save(
                    TrainingSession.builder()
                            .user(user)         //세션 소유자
                            .account(account)   //어떤 연습 계좌로 하는지
                            .symbol(picked)     //어떤 종목인지
                            .mode(req.mode())   //훈련 모드(랜덤 등)
                            .bars(req.bars())   //노출/사용할 봉 개수
                            .hiddenFutureBars(0)//(있다면) 미래봉 숨김 초기값
                            .startDate(finalStart)//봉 기준으로 확정한 시작일
                            .endDate(finalEnd)    //벙 기준으로 확정한 종료일
                            .status(TrainingStatus.IN_PROGRESS) //생성 즉시 진행중 처리(정책에 따라 READY도 가능)
                            .build()
            );

            // 4-6) 프론트가 즉시 차트 화면을 구성할 수 있도록
//      훈련 세션 생성 결과를 응답 DTO로 반환
            return new TrainingSessionCreateResponse(
                    saved.getId(),          // sessionId: 생성된 훈련 세션의 PK
                    account.getId(),        // accountId: 사용 중인 가상 계좌 ID
                    picked.getId(),         // symbolId: 훈련 종목의 내부 DB ID
                    picked.getTicker(),     // symbolTicker: 화면에 표시할 종목 코드
                    picked.getName(),       // symbolName: 화면에 표시할 종목명
                    saved.getMode(),        // mode: 훈련 모드 (RANDOM 등)
                    saved.getBars(),        // bars: 차트에 사용할 봉 개수
                    saved.getStartDate(),   // startDate: 봉 기준으로 확정된 시작 날짜
                    saved.getEndDate(),     // endDate: 봉 기준으로 확정된 종료 날짜
                    saved.getStatus()       // status: 현재 세션 상태 (IN_PROGRESS)
            );

        }

        //maxTries 동안 조건 만족 구간을 못 찾았으면 실패 처리
        throw new CustomException(ErrorCode.TRAINING_SESSION_CREATE_FAILED);
    }

    /**
     * 세션 기반 캔들 조회
     * - symbol/from/to를 클라이언트가 조작 못하게 "세션에서"만 가져온다.
     * - 즉, 프론트는 sessionId만 주고 서버가 세션 정보로 캔들 범위를 확정한다.
     */
    @Transactional(readOnly = true)
    public List<CandleDto> getSessionCandles(Long userId, Long sessionId){

        // 1) 세션 조회 + 소유권 검증 (내 세션만 접근)
        TrainingSession session = trainingSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        // 2) 세션에 저장된 날짜 범위를 KIS 요청 형식(YYYYMMDD)로 변환
        String from = session.getStartDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String to = session.getEndDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        // 3) 세션의 종목 티커로 KIS에서 캔들 조회
        return kisMarketDataService.getCandles(
                "J",
                session.getSymbol().getTicker(),
                from,
                to,
                "D",
                "0"
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
}
