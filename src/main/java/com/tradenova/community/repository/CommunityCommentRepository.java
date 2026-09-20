package com.tradenova.community.repository;

import com.tradenova.community.entity.CommunityComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {
    @EntityGraph(attributePaths = "user")
    Page<CommunityComment> findAllByPostIdOrderByCreatedAtAsc(Long postId, Pageable pageable);
    @Query("select c.post.id as id, count(c.id) as count from CommunityComment c where c.post.id in :ids group by c.post.id")
    List<CountView> countByPostIds(@Param("ids") Collection<Long> ids);
    long countByPostId(Long postId);
    void deleteAllByPostId(Long postId);
    interface CountView { Long getId(); long getCount(); }
}
