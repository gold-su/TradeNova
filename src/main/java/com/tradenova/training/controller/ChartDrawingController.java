package com.tradenova.training.controller;
import com.tradenova.training.dto.*; import com.tradenova.training.service.ChartDrawingService; import lombok.RequiredArgsConstructor; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/training/charts/{chartId}/drawings")
public class ChartDrawingController { private final ChartDrawingService service; private Long user(Authentication a){Object p=a.getPrincipal();return p instanceof Long?(Long)p:Long.valueOf(p.toString());}
 @GetMapping public List<ChartDrawingResponse> list(Authentication a,@PathVariable Long chartId){return service.list(user(a),chartId);}
 @PostMapping public ResponseEntity<ChartDrawingResponse> create(Authentication a,@PathVariable Long chartId,@RequestBody ChartDrawingCreateRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user(a),chartId,r));}
 @DeleteMapping("/{drawingId}") public ResponseEntity<Void> delete(Authentication a,@PathVariable Long chartId,@PathVariable Long drawingId){service.delete(user(a),chartId,drawingId);return ResponseEntity.noContent().build();}
 @DeleteMapping public ResponseEntity<Void> clear(Authentication a,@PathVariable Long chartId){service.clear(user(a),chartId);return ResponseEntity.noContent().build();}}
