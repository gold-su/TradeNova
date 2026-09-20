package com.tradenova.community.entity;

import com.tradenova.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "community_comment", indexes = @Index(name = "idx_community_comment_post", columnList = "post_id,created_at"))
public class CommunityComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "post_id", nullable = false) private CommunityPost post;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 2000) private String content;
    @CreationTimestamp @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
}
