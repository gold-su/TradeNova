package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.report.dto.TrainingEventResponse;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import com.tradenova.report.repository.TrainingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor // final 필드 생성자 주입 자동 생성
public class TrainingEventService {

    // TrainingEvent DB 접근용 repo
    private final TrainingEventRepository repo;

    /**
     * 이벤트 1건 추가 (append)
     * - 훈련 진행/거래/경고/AI평가 등 발생할 때마다 로그를 쌓는다.
     */
    @Transactional // 쓰기 작업이므로 트랜잭션 필요
    public TrainingEventResponse append(Long userId,
                                        Long chartId,
                                        Type type,
                                        String title,          // 사실상 "한 줄 로그 문자열" (엔티티에서는 summary)
                                        JsonNode payloadJson   // 상세 데이터(유연하게 JSON으로 저장)
    ) {
        // 입력값 방어
        if (type == null || title == null || chartId == null || title.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_TRAINING_EVENT);
        }
        
        // training_event 테이블에 INSERT
        TrainingEvent saved = repo.save(
                TrainingEvent.builder()
                        .userId(userId)          // 이벤트 발생한 사용자
                        .chartId(chartId)        // 어떤 차트에서 발생했는지
                        .type(type)              // 이벤트 종류
                        .summary(title)          // 화면에 바로 뿌릴 한 줄 로그
                        .payloadJson(payloadJson)// 상세 데이터 JSON
                        .build()
        );
        // 저장된 엔티티를 응답 DTO로 변환해서 반환
        return toRes(saved);
    }

    /**
     * 최신 이벤트 N개 조회
     * - 최신순(id desc)으로 size개 가져옴
     * - size는 1~200 범위로 보정
     */
    @Transactional(readOnly = true) // 조회 전용 트랜잭션
    public List<TrainingEventResponse> listLatest(Long userId, Long chartId, int size) {

        // size 방어 : 최소 1, 최대 200으로 클램프
        int n = Math.min(Math.max(size, 1), 200);

        // PageRequest.of(0, n) => 첫 페이지(0)에서 n개만 가져오겠다
        // repo 메서드는 최신순으로 가져오도록 "OrderByIdDesc"가 붙어있어야 함
        List<TrainingEventResponse> list = new java.util.ArrayList<>(
                repo.findAllByUserIdAndChartIdOrderByIdDesc(userId, chartId, PageRequest.of(0, n))
                        .stream()
                        .map(this::toRes) // 엔티티 -> DTO 변환
                        .toList()
        );
        
        // 최신 N개를 가져오되, 화면에서는 오래된 순 -> 최신 순으로 보여주기 위해 뒤집음
        // 위에서 repo를 ASC(오래된) 순으로 가져오면 '최근 N개' 개념이 꼬일 수 있음 
        java.util.Collections.reverse(list);

        return list;
    }

    /**
     * 이벤트 단건 조회
     * - eventId로 조회하되 userId까지 같이 걸어서 남의 이벤트 접근 차단
     */
    @Transactional(readOnly = true)
    public TrainingEventResponse getOne(Long userId, Long eventId) {

        // 내 이벤트가 아니면 조회 자체가 안 되도록 "findByIdAndUserId"
        TrainingEvent e = repo.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_EVENT_NOT_FOUND));
        return toRes(e);
    }

    /**
     * 엔티티 -> 응답 DTO 변환
     * - 컨트롤러/서비스에서 통일된 응답 형태 제공
     */
    private TrainingEventResponse toRes(TrainingEvent e) {
        return new TrainingEventResponse(
                e.getId(),              // eventId
                e.getChartId(),         // chartId
                e.getType().name(),     // type 문자열 (PROGRESS/TRADE/...)
                e.getSummary(),         // 한 줄 로그
                e.getPayloadJson(),     // 상세 JSON
                e.getCreatedAt()        // 생성 시각
        );
    }
}
