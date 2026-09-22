package com.tradenova.training.service;
import com.tradenova.common.exception.*; import com.tradenova.training.dto.*; import com.tradenova.training.entity.*; import com.tradenova.training.repository.*;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ChartDrawingService {
 private final TrainingSessionChartRepository charts; private final TrainingSessionRepository sessions; private final ChartDrawingRepository drawings;
 public List<ChartDrawingResponse> list(Long userId,Long chartId){ own(userId,chartId); return drawings.findAllByChart_IdOrderByCreatedAtAscIdAsc(chartId).stream().map(ChartDrawingResponse::from).toList(); }
 public List<ChartDrawingGroupResponse> listSession(Long userId, Long sessionId) {
  sessions.findByIdAndUserId(sessionId,userId).orElseThrow(()->new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));
  return drawings.findAllBySessionId(sessionId).stream().collect(java.util.stream.Collectors.groupingBy(d -> d.getChart().getId(), java.util.LinkedHashMap::new, java.util.stream.Collectors.mapping(ChartDrawingResponse::from, java.util.stream.Collectors.toList()))).entrySet().stream().map(e -> new ChartDrawingGroupResponse(e.getKey(), e.getValue())).toList();
 }
 @Transactional public ChartDrawingResponse create(Long userId,Long chartId,ChartDrawingCreateRequest r){ TrainingSessionChart c=own(userId,chartId); validate(r); return ChartDrawingResponse.from(drawings.save(ChartDrawing.builder().chart(c).type(r.type()).startDate(r.startDate()).startPrice(r.startPrice()).endDate(r.endDate()).endPrice(r.endPrice()).build())); }
 @Transactional public void delete(Long userId,Long chartId,Long drawingId){ own(userId,chartId); ChartDrawing d=drawings.findByIdAndChart_Id(drawingId,chartId).orElseThrow(()->new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND)); drawings.delete(d); }
 @Transactional public void clear(Long userId,Long chartId){ own(userId,chartId); drawings.deleteByChart_Id(chartId); }
 private TrainingSessionChart own(Long u,Long c){return charts.findByIdAndSession_User_Id(c,u).orElseThrow(()->new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));}
 private void validate(ChartDrawingCreateRequest r){ if(r==null||r.type()==null||r.startPrice()==null||r.startPrice().signum()<=0) throw new CustomException(ErrorCode.INVALID_REQUEST); if(r.type()!=ChartDrawingType.HORIZONTAL_LINE && (r.startDate()==null||r.endDate()==null||r.endPrice()==null||r.endPrice().signum()<=0)) throw new CustomException(ErrorCode.INVALID_REQUEST); if(r.type()==ChartDrawingType.HORIZONTAL_LINE && (r.startDate()!=null||r.endDate()!=null||r.endPrice()!=null)) throw new CustomException(ErrorCode.INVALID_REQUEST); }
}
