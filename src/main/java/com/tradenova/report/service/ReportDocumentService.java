package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.report.dto.ReportDocumentResponse;
import com.tradenova.report.dto.ReportDraftUpsertRequest;
import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.ReportKind;
import com.tradenova.report.repository.ReportDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 리포트 관련 비즈니스 로직 담당 서비스
 */
@Service
@RequiredArgsConstructor
public class ReportDocumentService {

    // ReportDocument DB 접근용 repository
    private final ReportDocumentRepository repo;

    private static final int SCHEMA_V1 = 1;

    /**
     * Draft 조회
     * - 없으면 null 반환(프론트에서 "초안 없음" 처리)
     */
    @Transactional(readOnly = true)
    public ReportDocumentResponse getDraft(Long userId, Long chartId) {
        // userId + chartId + kind = DRAFT 조건으로 Draft 문서 1개 조회
        return repo.findByUserIdAndChartIdAndKind(userId, chartId, ReportKind.DRAFT)
                .map(this::toRes) // 엔티티 -> DTO 변환
                .orElse(null); // DRAFT 없으면 null 반환
    }

    /**
     * Draft Upsert -> Draft를 있으면 수정, 없으면 생성
     * - (userId, chartId, DRAFT)는 1개만 유지
     * - contentJson(payload) 덮어쓰기
     * - Draft는 version=0 고정
     */
    @Transactional
    public ReportDocumentResponse upsertDraft(Long userId, Long chartId, ReportDraftUpsertRequest req) {

        // userId, chartId, kind = DRAFT 들로 기존 Draft 조회
        ReportDocument doc = repo.findTopByUserIdAndChartIdAndKind(userId, chartId, ReportKind.DRAFT)
                .orElseGet(() -> ReportDocument.builder() // 없으면 새 Draft 생성
                        .userId(userId)             // 작성 유저
                        .chartId(chartId)           // 어떤 차트인지
                        .kind(ReportKind.DRAFT)     // 문서 타입 == Draft
                        .schemaVersion(SCHEMA_V1)
                        .version(0) // Draft는 0 고정
                        .build());

        // Draft 내용을 저장
        doc.setContentJson(req.contentJson());

        // 혹시 기존 Draft에 schema/version이 null로 들어온 경우 대비
        if (doc.getSchemaVersion() == null) doc.setSchemaVersion(SCHEMA_V1);
        if (doc.getVersion() == null) doc.setVersion(0);

        // DB 저장, 기존은 update, 새 Draft는 insert
        ReportDocument saved = repo.save(doc);

        // 응답 DTO로 변환 후 반환
        return toRes(saved);
    }

    /**
     * Snapshot 생성 -> 여러개 저장 가능
     * - (userId, chartId, SNAPSHOT)은 여러 개 허용
     * - version을 1,2,3... 순번으로 증가
     */
    @Transactional
    public ReportDocumentResponse createSnapshot(Long userId, Long chartId,Long linkedEventId, JsonNode payloadJson) {
        // Version 가져오기 + 1
        int nextVersion = repo.findMaxVersionByUserIdAndChartIdAndKind(userId, chartId, ReportKind.SNAPSHOT)
                .map(v -> v + 1)
                .orElse(1);

        // 새 엔티티 생성
        ReportDocument saved = repo.save(
                ReportDocument.builder()
                        .userId(userId)             // 유저
                        .chartId(chartId)           // 어떤 차트 리포트인지
                        .kind(ReportKind.SNAPSHOT)  // 문서 타입 = SNAPSHOT
                        .schemaVersion(SCHEMA_V1)
                        .version(nextVersion)
                        .linkedEventId(linkedEventId)
                        .contentJson(payloadJson)   // 실제 리포트 내용
                        .build()
        );

        // DTO로 변환 후 반환
        return toRes(saved);
    }

    /**
     * Snapshot 목록(최신순)
     */
    @Transactional(readOnly = true)
    public List<ReportDocumentResponse> listSnapshots(Long userId, Long chartId) {
        // 스냅샷 목록 가져오기 후 반환
        return repo.findAllByUserIdAndChartIdAndKindOrderByCreatedAtDesc(userId, chartId, ReportKind.SNAPSHOT)
                .stream()
                .map(this::toRes)
                .toList();
    }

    // 엔티티 -> DTO 변환 메서드
    private ReportDocumentResponse toRes(ReportDocument d) {
        return new ReportDocumentResponse(
                d.getId(),          // 리포트 ID
                d.getChartId(),     // 차트 ID
                d.getKind().name(), // 문서 타입 문자열
                d.getContentJson(), // 리포트 내용(JSON)
                d.getCreatedAt(),   // 생성 시간
                d.getUpdatedAt()    // 수정 시간
        );
    }
}
