package com.tradenova.training.repository;
import com.tradenova.training.entity.ChartDrawing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ChartDrawingRepository extends JpaRepository<ChartDrawing,Long> {
 List<ChartDrawing> findAllByChart_IdOrderByCreatedAtAscIdAsc(Long chartId);
 Optional<ChartDrawing> findByIdAndChart_Id(Long id, Long chartId);
 void deleteByChart_Id(Long chartId);
 @Query("select d from ChartDrawing d join fetch d.chart c where c.session.id = :sessionId order by c.id asc, d.createdAt asc, d.id asc")
 List<ChartDrawing> findAllBySessionId(@Param("sessionId") Long sessionId);
}
