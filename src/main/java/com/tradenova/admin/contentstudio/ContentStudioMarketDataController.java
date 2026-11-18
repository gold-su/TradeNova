package com.tradenova.admin.contentstudio;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/content-studio/market-data")
public class ContentStudioMarketDataController {

    private final ContentStudioMarketDataCollector collector;

    @PostMapping("/dry-run")
    public ResponseEntity<ContentStudioCollectionPlanResponse> dryRun(
            @Valid @RequestBody ContentStudioCollectionRequest request
    ) {
        return ResponseEntity.ok(collector.dryRun(request));
    }

    @PostMapping("/collect")
    public ResponseEntity<ContentStudioCollectionExecutionResponse> collect(
            @Valid @RequestBody ContentStudioCollectionExecuteRequest request
    ) {
        return ResponseEntity.ok(collector.execute(request));
    }
}
