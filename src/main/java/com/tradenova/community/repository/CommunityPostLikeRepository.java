package com.tradenova.community.repository;

import com.tradenova.community.entity.CommunityPostLike;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, Long> {
    Optional<CommunityPostLike> findByPostIdAndUserId(Long postId, Long userId);
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);
    void deleteAllByPostId(Long postId);
    @Query("select l.post.id as id, count(l.id) as count from CommunityPostLike l where l.post.id in :ids group by l.post.id")
    List<CountView> countByPostIds(@Param("ids") Collection<Long> ids);
    @Query("select l.post.id from CommunityPostLike l where l.user.id = :userId and l.post.id in :ids")
    Set<Long> findLikedPostIds(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);
    interface CountView { Long getId(); long getCount(); }

    @Modifying
    @Query(value = "insert ignore into community_post_like(post_id,user_id,created_at) values (:postId,:userId,current_timestamp)", nativeQuery = true)
    int insertIgnore(@Param("postId") Long postId, @Param("userId") Long userId);
}
