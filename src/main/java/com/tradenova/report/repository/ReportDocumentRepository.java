package com.tradenova.report.repository;

import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.ReportKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // Draft는 chart당 1개 (uq로 보장)
    Optional<ReportDocument> findByUserIdAndChartIdAndKind(Long userId, Long chartId, ReportKind kind);

    // Snapshot 리스트
    List<ReportDocument> findAllByUserIdAndChartIdAndKindOrderByVersionDesc(
            Long userId, Long chartId, ReportKind kind
    );

    // Snapshot "다음 version" 계산용: 가장 큰 version 1개
    Optional<ReportDocument> findTopByUserIdAndChartIdAndKindOrderByVersionDesc(
            Long userId, Long chartId, ReportKind kind
    );

    // (옵션) 특정 이벤트에 연결된 스냅샷 조회
    List<ReportDocument> findAllByUserIdAndChartIdAndLinkedEventIdOrderByVersionDesc(
            Long userId, Long chartId, Long linkedEventId
    );
    // 유저 소유권 검증 + 단건 조회
    Optional<ReportDocument> findByIdAndUserId(Long id, Long userId);

    @Query("select max(d.version) from ReportDocument d where d.userId = :userId and d.chartId = :chartId and d.kind = :kind")
    Optional<Integer> findMaxVersionByUserIdAndChartIdAndKind(@Param("userId") Long userId,
                                                              @Param("chartId") Long chartId,
                                                              @Param("kind") ReportKind kind);
}
