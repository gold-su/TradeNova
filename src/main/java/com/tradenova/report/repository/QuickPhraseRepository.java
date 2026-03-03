package com.tradenova.report.repository;

import com.tradenova.report.entity.QuickPhrase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuickPhraseRepository extends JpaRepository<QuickPhrase, Long> {

    // 유저의 빠른 문장 목록 (정렬 : sortOrder -> id)
    List<QuickPhrase> findByUserIdOrderBySortOrderAscIdAsc(Long userId);

    // 유저 소유권 검증용 (수정/삭제 시)
    Optional<QuickPhrase> findByIdAndUserId(Long id, Long userId);

    // 제목 중복 체크(원하면 사용)
    boolean existsByUserIdAndTitle(Long userId, String title);
}
