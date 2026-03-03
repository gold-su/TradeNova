package com.tradenova.report.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.report.dto.QuickPhraseCreateRequest;
import com.tradenova.report.dto.QuickPhraseResponse;
import com.tradenova.report.dto.QuickPhraseUpdateRequest;
import com.tradenova.report.entity.QuickPhrase;
import com.tradenova.report.repository.QuickPhraseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuickPhraseService {

    // QuickPhrase DB 접근용 Repository
    private final QuickPhraseRepository repo;

    // 조회 전용 트랜잭션
    @Transactional(readOnly = true)
    // 사용자의 매크로 목록 조회 메서드
    public List<QuickPhraseResponse> list(Long userId) {
        return repo.findAllByUserIdOrderBySortOrderAscIdAsc(userId)// 사용자별 문장을 sortOrder 오름차순, id 오름차순 으로 정렬 조회
                .stream()
                // 엔티티 -> 응답 DTO 변환
                .map(p -> new QuickPhraseResponse(p.getId(), p.getTitle(), p.getContent(), p.getSortOrder()))
                .toList();
    }

    @Transactional
    // 새 매크로 문장 생성 후 맨 마지막 순서로 추가
    public QuickPhraseResponse create(Long userId, QuickPhraseCreateRequest req) {
        // (선택) 제목 중복 막고 싶으면
        // if (repo.existsByUserIdAndTitle(userId, req.title())) throw new CustomException(ErrorCode.DUPLICATE_TITLE);

        // ===== 1차 방어 (null 체크) =====
        if (req == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // ===== 2차 방어 (title 검증) =====
        String title = req.title();
        if (title == null || title.isBlank()) {
            throw new CustomException(ErrorCode.QUICK_PHRASE_TITLE_REQUIRED);
        }
        if (title.length() > 40) {
            throw new CustomException(ErrorCode.QUICK_PHRASE_TITLE_TOO_LONG);
        }

        // ===== 3차 방어 (content 검증) =====
        String content = req.content();
        if (content == null || content.isBlank()) {
            throw new CustomException(ErrorCode.QUICK_PHRASE_CONTENT_REQUIRED);
        }
        if (content.length() > 2000) {
            throw new CustomException(ErrorCode.QUICK_PHRASE_CONTENT_TOO_LONG);
        }

        // 현재 사용자 문장 목록 다 가져와서
        // 현재 버튼이 1,2,3 있다면 새로 만들면 -> 4
        Integer nextSort = repo.findAllByUserIdOrderBySortOrderAscIdAsc(userId).stream()
                .map(QuickPhrase::getSortOrder)     // sortOrder만 추출
                .filter(x -> x != null)      // null 제외
                .max(Integer::compareTo)           // 최대값 찾음
                .map(x -> x + 1)            // +1
                .orElse(1);                  // 없으면 1부터 시작

        // 새 엔티티 생성 후 DB 저장
        QuickPhrase saved = repo.save(
                QuickPhrase.builder()
                        .userId(userId)
                        .title(title.trim())
                        .content(content.trim())
                        .sortOrder(nextSort)
                        .build()
        );
        // 저장 결과 DTO 반환
        return new QuickPhraseResponse(saved.getId(), saved.getTitle(), saved.getContent(), saved.getSortOrder());
    }

    @Transactional
    // 본인 특정 문장 수정 + 자동 DB 반영
    public QuickPhraseResponse update(Long userId, Long phraseId, QuickPhraseUpdateRequest req) {
        // userId 포함 조회
        // 다른 사용자 문장 접근 차단
        QuickPhrase p = repo.findByIdAndUserId(phraseId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUICK_PHRASE_NOT_FOUND));

        // 제목 수정, JPA는 변경 감지로 자동 UPDATE
        p.setTitle(req.title());
        // 내용 수정, JPA는 변경 감지로 자동 UPDATE
        p.setContent(req.content());
        // 수정된 값 반환
        return new QuickPhraseResponse(p.getId(), p.getTitle(), p.getContent(), p.getSortOrder());
    }

    @Transactional
    public void delete(Long userId, Long phraseId) {
        // 본인 매크로 조회 (소유권 확인)
        QuickPhrase p = repo.findByIdAndUserId(phraseId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUICK_PHRASE_NOT_FOUND));

        // 삭제
        repo.delete(p);
    }
}
