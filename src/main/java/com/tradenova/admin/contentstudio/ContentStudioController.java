package com.tradenova.admin.contentstudio;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/content-studio")
public class ContentStudioController {

    private final ContentStudioService contentStudioService;

    @GetMapping("/access")
    public Map<String, Boolean> access() {
        return Map.of("allowed", true);
    }

    @PostMapping("/cases/random")
    public ResponseEntity<BlindContentCaseResponse> randomCase(
            @Valid @RequestBody(required = false) ContentStudioRandomRequest request
    ) {
        return ResponseEntity.ok(contentStudioService.randomCase(request));
    }

    @GetMapping("/cases/{caseKey}")
    public ResponseEntity<BlindContentCaseResponse> loadCase(
            @PathVariable String caseKey,
            @RequestParam(required = false) Integer throughIndex
    ) {
        return ResponseEntity.ok(contentStudioService.loadBlindCase(caseKey, throughIndex));
    }

    @GetMapping("/cases/{caseKey}/reveal")
    public ResponseEntity<ContentCaseRevealResponse> reveal(@PathVariable String caseKey) {
        return ResponseEntity.ok(contentStudioService.reveal(caseKey));
    }
}
