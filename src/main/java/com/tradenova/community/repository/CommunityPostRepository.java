package com.tradenova.community.repository;

import com.tradenova.community.entity.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
    @EntityGraph(attributePaths = "user")
    Page<CommunityPost> findAllByOrderByCreatedAtDesc(Pageable pageable);
    @EntityGraph(attributePaths = "user")
    @Query("select p from CommunityPost p where p.id = :id")
    java.util.Optional<CommunityPost> findDetailById(Long id);
}
