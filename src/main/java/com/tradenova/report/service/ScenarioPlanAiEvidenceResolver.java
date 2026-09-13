package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.report.dto.ScenarioPlanAiEvidence;
import com.tradenova.report.dto.TradeActionAiEvidence;
import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.ReportKind;
import org.springframework.stereotype.Component;

/** Validates an explicit action-to-Scenario link without temporal or latest-snapshot fallback. */
@Component
public class ScenarioPlanAiEvidenceResolver {

    public ScenarioPlanAiEvidence resolve(TradeActionAiEvidence action, ReportDocument document,
                                          Long userId, Long chartId) {
        if (action == null || !"SCENARIO".equals(action.reasonMode())
                || action.scenarioSnapshotId() == null || document == null
                || !action.scenarioSnapshotId().equals(document.getId())
                || !userId.equals(document.getUserId()) || !chartId.equals(document.getChartId())
                || !chartId.equals(action.chartId()) || document.getKind() != ReportKind.SNAPSHOT
                || !hasScenarioTag(document.getContentJson())) return null;
        JsonNode content = document.getContentJson();
        return new ScenarioPlanAiEvidence(document.getId(), document.getChartId(), document.getVersion(),
                document.getCreatedAt(), text(content, "thesis"), text(content, "entryReason"),
                text(content, "exitPlan"), text(content, "riskNote"), text(content, "freeNote"));
    }

    private boolean hasScenarioTag(JsonNode content) {
        JsonNode tags = content == null ? null : content.get("tags");
        if (tags == null || !tags.isArray()) return false;
        for (JsonNode tag : tags) {
            if (tag.isTextual() && "SCENARIO".equals(tag.textValue())) return true;
        }
        return false;
    }

    private String text(JsonNode content, String field) {
        JsonNode value = content == null ? null : content.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
}
