package com.tradenova.community.entity;

import com.tradenova.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "community_post_like", uniqueConstraints = @UniqueConstraint(name = "uq_community_like_user_post", columnNames = {"user_id","post_id"}))
public class CommunityPostLike {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "post_id", nullable = false) private CommunityPost post;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @CreationTimestamp @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
}
