package com.tradenova.training.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name="chart_drawing", indexes=@Index(name="idx_chart_drawing_chart", columnList="chart_id"))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED) @AllArgsConstructor @Builder
public class ChartDrawing {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="chart_id", nullable=false) private TrainingSessionChart chart;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ChartDrawingType type;
 private LocalDate startDate;
 @Column(nullable=false,precision=19,scale=4) private BigDecimal startPrice;
 private LocalDate endDate;
 @Column(precision=19,scale=4) private BigDecimal endPrice;
 @Column(name="anchor3_date") private LocalDate anchor3Date;
 @Column(name="anchor3_price", precision=19,scale=4) private BigDecimal anchor3Price;
 @Column(name="text_content", length=500) private String textContent;
 @Column(name="options_json", length=2000) private String optionsJson;
 @CreationTimestamp @Column(nullable=false,updatable=false) private OffsetDateTime createdAt;
 @UpdateTimestamp @Column(nullable=false) private OffsetDateTime updatedAt;
}
