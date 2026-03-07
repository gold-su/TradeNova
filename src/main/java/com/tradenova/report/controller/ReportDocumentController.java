package com.tradenova.report.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.report.dto.ReportDocumentResponse;
import com.tradenova.report.dto.ReportDraftUpsertRequest;
import com.tradenova.report.dto.ReportSnapshotCreateRequest;
import com.tradenova.report.service.ReportDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportDocumentController {

    private final ReportDocumentService service;

    // Draft 조회
    // GET /api/reports/charts/{chartId}/draft
    @GetMapping("/charts/{chartId}/draft")
    public ResponseEntity<ReportDocumentResponse> getDraft(
            Authentication auth,
            @PathVariable Long chartId
    ) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(service.getDraft(userId, chartId));
    }

    // Draft upsert
    // PUT /api/reports/charts/{chartId}/draft
    @PutMapping("/charts/{chartId}/draft")
    public ResponseEntity<ReportDocumentResponse> upsertDraft(
            Authentication auth,
            @PathVariable Long chartId,
            @RequestBody ReportDraftUpsertRequest req
    ) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(service.upsertDraft(userId, chartId, req));
    }

    // Snapshot 생성 (수동 저장 버튼 같은 거)
    // POST /api/reports/charts/{chartId}/snapshots
    @PostMapping("/charts/{chartId}/snapshots")
    public ResponseEntity<ReportDocumentResponse> createSnapshot(
            Authentication auth,
            @PathVariable Long chartId,
            @Valid @RequestBody ReportSnapshotCreateRequest req
    ) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(
                service.createSnapshot(userId, chartId, req.linkedEventId(), req.contentJson())
        );
    }

    // Snapshot 목록(최신순)
    // GET /api/reports/charts/{chartId}/snapshots
    @GetMapping("/charts/{chartId}/snapshots")
    public ResponseEntity<List<ReportDocumentResponse>> listSnapshots(
            Authentication auth,
            @PathVariable Long chartId
    ) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(service.listSnapshots(userId, chartId));
    }

    private Long extractUserId(Authentication authentication) {
        Object p = authentication.getPrincipal();
        return (p instanceof Long) ? (Long) p : Long.valueOf(p.toString());
    }


}
