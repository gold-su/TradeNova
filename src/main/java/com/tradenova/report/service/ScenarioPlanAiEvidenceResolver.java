package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.report.dto.ScenarioPlanAiEvidence;
import com.tradenova.report.dto.TradeActionAiEvidence;
import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.ReportKind;
import org.springframework.stereotype.Component;
import java.util.*;

/** Resolves only an exact SCENARIO id; no latest/timestamp fallback exists. */
@Component
public class ScenarioPlanAiEvidenceResolver {
    public TradeActionAiEvidence link(TradeActionAiEvidence action, Long userId, Map<Long, ReportDocument> snapshots) {
        if (action == null || !"SCENARIO".equals(action.reasonMode()) || action.scenarioSnapshotId() == null) {
            return action;
        }
        ReportDocument d = snapshots.get(action.scenarioSnapshotId());
        if (d == null || !Objects.equals(d.getUserId(), userId) || !Objects.equals(d.getChartId(), action.chartId())
                || d.getKind() != ReportKind.SNAPSHOT || !hasScenarioTag(d.getContentJson())) {
            return action;
        }
        ScenarioPlanAiEvidence plan = new ScenarioPlanAiEvidence(
                d.getId(), d.getChartId(), d.getVersion(), d.getCreatedAt(),
                text(d.getContentJson(), "thesis"), text(d.getContentJson(), "entryReason"),
                text(d.getContentJson(), "exitPlan"), text(d.getContentJson(), "riskNote"),
                text(d.getContentJson(), "freeNote"));
        return new TradeActionAiEvidence(
                action.eventId(), action.chartId(), action.tradeId(), action.side(), action.candleTime(),
                action.qty(), action.price(), action.createdAt(), action.reasons(), action.timeline(),
                action.reasonMode(), action.scenarioSnapshotId(), plan);
    }

    private boolean hasScenarioTag(JsonNode content) {
        if (content == null || !content.path("tags").isArray()) return false;
        for (JsonNode tag : content.path("tags")) {
            if (tag.isTextual() && "SCENARIO".equals(tag.textValue())) return true;
        }
        return false;
    }

    private String text(JsonNode content, String field) {
        JsonNode value = content == null ? null : content.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
