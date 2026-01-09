package com.tradenova.symbol.controller;

import com.tradenova.symbol.dto.SymbolResponse;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.symbol.repository.SymbolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController //REST API 컨트롤러임을 선언
@RequiredArgsConstructor //final 필드에 대해 생성자 자동 생성
@RequestMapping("/api/symbols") //이 컨트롤러의 공통 URL prefix
//종목 조회/검색 API 컨트롤러
public class SymbolController {
    
    //Symbol DB 접근용 Repository (생성자 주입)
    private final SymbolRepository symbolRepository;

    /**
     * 전체 활성 심볼 조회
     * GET /api/symbols
     */
    @GetMapping
    public ResponseEntity<List<SymbolResponse>> listActive() {
        
        //active = true 인 종목을 ID 오름차순으로 조회
        List<SymbolResponse> res = symbolRepository.findAllByActiveTrueOrderByIdAsc()
                .stream()                   //조회 결과를 스트림으로 변환
                .map(SymbolResponse::from)  //엔티티 -> 응답 DTO 변환
                .toList();                  //List로 다시 수집

        //HTTP 200 OK + JSON 응답
        return ResponseEntity.ok(res);
    }

    /**
     * 단건 조회
     * GET /api/symbols/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<SymbolResponse> getOne(@PathVariable Long id) {

        //id로 Symbol 조회 (없으면 예외 발생)
        Symbol s = symbolRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("SYMBOL_NOT_FOUND: id=" + id));

        //엔티티 -> DTO 변환 후 반환
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
        //입력값 앞뒤 공백 제거
        String keyword = q.trim();

        //공백만 들어오면 빈 리스트 반환 (불필요한 DB 조회 방지)
        if (keyword.isBlank()) return ResponseEntity.ok(List.of());

        //keyword가 전부 숫자인지 검사 -> 티커처럼 보이는지 판단
        boolean looksLikeTicker = keyword.chars().allMatch(Character::isDigit);

        //숫자면 ticker 검색, 아니면 name 검색
        List<Symbol> list = looksLikeTicker
                ? symbolRepository.findTop50ByActiveTrueAndTickerContainingOrderByTickerAsc(keyword)
                : symbolRepository.findTop50ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(keyword);

        //조회 결과를 DTO로 변환해서 반환
        return ResponseEntity.ok(list.stream().map(SymbolResponse::from).toList());
    }
}
