package com.tradenova.community.entity;

import com.tradenova.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "community_report", uniqueConstraints = @UniqueConstraint(name = "uq_community_reporter_target", columnNames = {"reporter_id","target_type","target_id"}))
public class CommunityReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reporter_id", nullable = false) private User reporter;
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 20) private TargetType targetType;
    @Column(name = "target_id", nullable = false) private Long targetId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ReportReason reason;
    @CreationTimestamp @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    public enum TargetType { POST }
    public enum ReportReason { SPAM, HARASSMENT, INAPPROPRIATE, MISINFORMATION, OTHER }
}
