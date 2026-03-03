package com.tradenova.report.repository;

import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.ReportKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportDocumentRepository extends JpaRepository<ReportDocument, Long> {

    // 특정 차트의 Draft 1개 가져오기(없으면 null)
    Optional<ReportDocument> findTopByUserIdAndChartIdAndKind(Long userId, Long chartId, ReportKind kind);

    // 차트별 Snapshot 리스트(최신순)
    List<ReportDocument> findAllByUserIdAndChartIdAndKindOrderByCreatedAtDesc(
            Long userId,
            Long chartId,
            ReportKind kind
    );

    // 유저 소유권 검증 + 단건 조회
    Optional<ReportDocument> findByIdAndUserId(Long id, Long userId);
}
