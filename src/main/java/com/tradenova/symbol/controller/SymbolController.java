package com.tradenova.symbol.controller;

import com.tradenova.symbol.dto.SymbolResponse;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.symbol.repository.SymbolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/symbols")
public class SymbolController {

    private final SymbolRepository symbolRepository;

    /**
     * 전체 활성 심볼 조회
     * GET /api/symbols
     */
    @GetMapping
    public ResponseEntity<List<SymbolResponse>> listActive() {
        List<SymbolResponse> res = symbolRepository.findAllByActiveTrueOrderByIdAsc()
                .stream()
                .map(SymbolResponse::from)
                .toList();

        return ResponseEntity.ok(res);
    }

    /**
     * 단건 조회
     * GET /api/symbols/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<SymbolResponse> getOne(@PathVariable Long id) {

        Symbol s = symbolRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("SYMBOL_NOT_FOUND: id=" + id));

        return ResponseEntity.ok(SymbolResponse.from(s));
    }

    /**
     * 검색 (name 또는 ticker)
     * GET /api/symbol/search?q=삼성
     *
     * - q가 숫자 위주면 ticker 검색
     * - 아니면 name 검색
     */
    @GetMapping("/search")
    public ResponseEntity<List<SymbolResponse>> search(@RequestParam String q) {
        String keyword = q.trim();
        if (keyword.isBlank()) return ResponseEntity.ok(List.of());

        boolean looksLikeTicker = keyword.chars().allMatch(Character::isDigit);

        List<Symbol> list = looksLikeTicker
                ? symbolRepository.findTop50ByActiveTrueAndTickerContainingOrderByTickerAsc(keyword)
                : symbolRepository.findTop50ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(keyword);

        return ResponseEntity.ok(list.stream().map(SymbolResponse::from).toList());
    }
}
