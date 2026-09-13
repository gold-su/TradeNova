package com.tradenova.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.report.dto.TradeActionAiEvidence;
import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.ReportKind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioPlanAiEvidenceResolverTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScenarioPlanAiEvidenceResolver resolver = new ScenarioPlanAiEvidenceResolver();

    @Test
    void linksValidExplicitScenarioAndIgnoresNewerSnapshot() {
        TradeActionAiEvidence linked = resolver.link(action("SCENARIO", 55L), 1L,
                Map.of(55L, snapshot(55L, 1L, 10L, ReportKind.SNAPSHOT, "SCENARIO", 1),
                        56L, snapshot(56L, 1L, 10L, ReportKind.SNAPSHOT, "SCENARIO", 2)));
        assertNotNull(linked.scenarioPlan());
        assertEquals(55L, linked.scenarioPlan().snapshotId());
        assertEquals(1, linked.scenarioPlan().version());
    }

    @Test
    void rejectsWrongChartUserKindMissingTagAndDifferentTagCasing() {
        assertRejected(snapshot(55L, 1L, 99L, ReportKind.SNAPSHOT, "SCENARIO", 1));
        assertRejected(snapshot(55L, 2L, 10L, ReportKind.SNAPSHOT, "SCENARIO", 1));
        assertRejected(snapshot(55L, 1L, 10L, ReportKind.DRAFT, "SCENARIO", 1));
        assertRejected(snapshot(55L, 1L, 10L, ReportKind.SNAPSHOT, null, 1));
        assertRejected(snapshot(55L, 1L, 10L, ReportKind.SNAPSHOT, "scenario", 1));
        assertRejected(snapshot(55L, 1L, 10L, ReportKind.SNAPSHOT, "Scenario", 1));
    }

    @Test
    void manualMissingAndUnknownReferencesKeepActionButDoNotLinkPlan() {
        ReportDocument scenario = snapshot(55L, 1L, 10L, ReportKind.SNAPSHOT, "SCENARIO", 1);
        TradeActionAiEvidence manual = resolver.link(action("MANUAL", 55L), 1L, Map.of(55L, scenario));
        TradeActionAiEvidence missingId = resolver.link(action("SCENARIO", null), 1L, Map.of(55L, scenario));
        TradeActionAiEvidence unknown = resolver.link(action("SCENARIO", 404L), 1L, Map.of(55L, scenario));
        assertAll(() -> assertNull(manual.scenarioPlan()), () -> assertNull(missingId.scenarioPlan()),
                () -> assertNull(unknown.scenarioPlan()), () -> assertEquals(101L, unknown.tradeId()),
                () -> assertEquals(404L, unknown.scenarioSnapshotId()));
    }

    @Test
    void neverFallsBackByTimestampOrLatestSnapshot() {
        TradeActionAiEvidence result = resolver.link(action("SCENARIO", 404L), 1L,
                Map.of(55L, snapshot(55L, 1L, 10L, ReportKind.SNAPSHOT, "SCENARIO", 1),
                        56L, snapshot(56L, 1L, 10L, ReportKind.SNAPSHOT, "SCENARIO", 2)));
        assertNull(result.scenarioPlan());
    }

    private void assertRejected(ReportDocument document) {
        TradeActionAiEvidence result = resolver.link(action("SCENARIO", 55L), 1L, Map.of(55L, document));
        assertNotNull(result);
        assertNull(result.scenarioPlan());
    }

    private TradeActionAiEvidence action(String mode, Long snapshotId) {
        return new TradeActionAiEvidence(1L, 10L, 101L, "BUY", 22L, null, null,
                Instant.EPOCH, List.of(), null, mode, snapshotId, null);
    }

    private ReportDocument snapshot(Long id, Long userId, Long chartId, ReportKind kind, String tag, int version) {
        ObjectNode content = mapper.createObjectNode().put("thesis", "plan " + id)
                .put("entryReason", "entry " + id).put("exitPlan", "exit")
                .put("riskNote", "risk").put("freeNote", "note");
        if (tag != null) content.putArray("tags").add(tag);
        return ReportDocument.builder().id(id).userId(userId).chartId(chartId).kind(kind).version(version)
                .contentJson(content).createdAt(Instant.ofEpochSecond(id)).build();
    }
}
