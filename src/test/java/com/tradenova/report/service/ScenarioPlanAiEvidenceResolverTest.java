package com.tradenova.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.report.dto.TradeActionAiEvidence;
import com.tradenova.report.entity.EventOrigin;
import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.ReportKind;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioPlanAiEvidenceResolverTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TradeActionAiEvidenceResolver actionResolver = new TradeActionAiEvidenceResolver();
    private final ScenarioPlanAiEvidenceResolver resolver = new ScenarioPlanAiEvidenceResolver();

    @Test
    void requiresScenarioModeOwnerChartKindAndExactScenarioTag() {
        TradeActionAiEvidence action = action("SCENARIO", 55L);
        ReportDocument valid = document(55L, 1L, 10L, ReportKind.SNAPSHOT, true);
        assertNotNull(resolver.resolve(action, valid, 1L, 10L));
        assertNull(resolver.resolve(action, document(55L, 1L, 11L, ReportKind.SNAPSHOT, true), 1L, 10L));
        assertNull(resolver.resolve(action, document(55L, 2L, 10L, ReportKind.SNAPSHOT, true), 1L, 10L));
        assertNull(resolver.resolve(action, document(55L, 1L, 10L, ReportKind.DRAFT, true), 1L, 10L));
        assertNull(resolver.resolve(action, document(55L, 1L, 10L, ReportKind.SNAPSHOT, false), 1L, 10L));
        assertNull(resolver.resolve(action, null, 1L, 10L));
    }

    @Test
    void manualAndLegacyActionsRemainValidButNeverLinkScenario() {
        ReportDocument valid = document(55L, 1L, 10L, ReportKind.SNAPSHOT, true);
        TradeActionAiEvidence manual = action("MANUAL", 55L);
        TradeActionAiEvidence legacy = action(null, null);
        assertNotNull(manual);
        assertNotNull(legacy);
        assertNull(resolver.resolve(manual, valid, 1L, 10L));
        assertNull(resolver.resolve(legacy, valid, 1L, 10L));
    }

    private TradeActionAiEvidence action(String mode, Long snapshotId) {
        ObjectNode payload = mapper.createObjectNode().put("tradeId", 101L).put("side", "BUY");
        if (mode != null) payload.put("reasonMode", mode);
        if (snapshotId != null) payload.put("scenarioSnapshotId", snapshotId);
        payload.putArray("reasons").addObject().put("entryReason", "reason");
        TrainingEvent event = TrainingEvent.builder().id(1L).userId(1L).chartId(10L).type(Type.TRADE)
                .origin(EventOrigin.USER).summary("trade").payloadJson(payload).createdAt(Instant.EPOCH).build();
        return actionResolver.parse(event, Map.of());
    }

    private ReportDocument document(Long id, Long user, Long chart, ReportKind kind, boolean tagged) {
        ObjectNode content = mapper.createObjectNode().put("entryReason", "plan");
        content.putArray("tags").add(tagged ? "SCENARIO" : "scenario");
        return ReportDocument.builder().id(id).userId(user).chartId(chart).kind(kind).version(1)
                .contentJson(content).createdAt(Instant.EPOCH).build();
    }
}
