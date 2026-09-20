package com.tradenova.community.service;

import com.tradenova.community.dto.CommunityDtos.*;
import com.tradenova.community.entity.*;
import com.tradenova.community.repository.*;
import com.tradenova.common.exception.*;
import com.tradenova.user.entity.User;
import com.tradenova.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class CommunityService {
    private final CommunityPostRepository posts; private final CommunityCommentRepository comments;
    private final CommunityPostLikeRepository likes; private final CommunityReportRepository reports;
    private final UserRepository users; private final CommunityProfileService profiles;

    @Transactional(readOnly=true)
    public Page<PostSummary> list(Long userId, Pageable pageable) {
        Page<CommunityPost> page = posts.findAllByOrderByCreatedAtDesc(pageable);
        List<Long> ids = page.map(CommunityPost::getId).toList();
        Map<Long,Long> likeCounts = counts(likes.countByPostIds(ids));
        Map<Long,Long> commentCounts = commentCounts(comments.countByPostIds(ids));
        Set<Long> liked = ids.isEmpty()?Set.of():likes.findLikedPostIds(userId, ids);
        Map<Long,Author> authors = profiles.profiles(page.map(CommunityPost::getUser).toList());
        return page.map(p -> new PostSummary(p.getId(), p.getTitle(), preview(p.getContent()), authors.get(p.getUser().getId()), likeCounts.getOrDefault(p.getId(),0L), commentCounts.getOrDefault(p.getId(),0L), liked.contains(p.getId()), p.getCreatedAt()));
    }

    @Transactional(readOnly=true)
    public PostDetail detail(Long userId, Long id) { CommunityPost p = post(id); Author a=profiles.profiles(List.of(p.getUser())).get(p.getUser().getId()); return detail(userId,p,a); }
    @Transactional public PostDetail create(Long userId, PostRequest r) { User u=user(userId); CommunityPost p=posts.save(CommunityPost.builder().user(u).title(r.title().trim()).content(r.content().trim()).build()); return detail(userId,p,profiles.profiles(List.of(u)).get(userId)); }
    @Transactional public PostDetail update(Long userId, Long id, PostRequest r) { CommunityPost p=post(id); owner(userId,p.getUser().getId()); p.setTitle(r.title().trim()); p.setContent(r.content().trim()); return detail(userId,p,profiles.profiles(List.of(p.getUser())).get(userId)); }
    @Transactional public void deletePost(Long userId, Long id) { CommunityPost p=post(id); owner(userId,p.getUser().getId()); comments.deleteAllByPostId(id); likes.deleteAllByPostId(id); reports.deleteAllByTargetTypeAndTargetId(CommunityReport.TargetType.POST,id); posts.delete(p); }
    @Transactional(readOnly=true) public Page<Comment> listComments(Long userId,Long postId,Pageable pageable){ post(postId); Page<CommunityComment> page=comments.findAllByPostIdOrderByCreatedAtAsc(postId,pageable); Map<Long,Author> a=profiles.profiles(page.map(CommunityComment::getUser).toList()); return page.map(c->new Comment(c.getId(),a.get(c.getUser().getId()),c.getContent(),c.getUser().getId().equals(userId),c.getCreatedAt())); }
    @Transactional public Comment addComment(Long userId,Long postId,CommentRequest r){ User u=user(userId); CommunityComment c=comments.save(CommunityComment.builder().post(post(postId)).user(u).content(r.content().trim()).build()); return new Comment(c.getId(),profiles.profiles(List.of(u)).get(userId),c.getContent(),true,c.getCreatedAt()); }
    @Transactional public void deleteComment(Long userId,Long id){ CommunityComment c=comments.findById(id).orElseThrow(()->new CustomException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND)); owner(userId,c.getUser().getId()); comments.delete(c); }
    @Transactional public Like like(Long userId,Long postId){ post(postId); user(userId); likes.insertIgnore(postId,userId); return new Like(true,likes.countByPostId(postId)); }
    @Transactional public Like unlike(Long userId,Long postId){ post(postId); likes.findByPostIdAndUserId(postId,userId).ifPresent(likes::delete); return new Like(false,likes.countByPostId(postId)); }
    @Transactional public Report report(Long userId,ReportRequest r){ if(r.targetType()!=CommunityReport.TargetType.POST || !posts.existsById(r.targetId())) throw new CustomException(ErrorCode.COMMUNITY_TARGET_NOT_FOUND); if(reports.existsByReporterIdAndTargetTypeAndTargetId(userId,r.targetType(),r.targetId())) throw new CustomException(ErrorCode.COMMUNITY_ALREADY_REPORTED); CommunityReport saved=reports.save(CommunityReport.builder().reporter(user(userId)).targetType(r.targetType()).targetId(r.targetId()).reason(r.reason()).build()); return new Report(saved.getId()); }

    private PostDetail detail(Long uid,CommunityPost p,Author a){ return new PostDetail(p.getId(),p.getTitle(),p.getContent(),a,likes.countByPostId(p.getId()),comments.countByPostId(p.getId()),likes.existsByPostIdAndUserId(p.getId(),uid),p.getUser().getId().equals(uid),p.getCreatedAt(),p.getUpdatedAt()); }
    private CommunityPost post(Long id){return posts.findDetailById(id).orElseThrow(()->new CustomException(ErrorCode.COMMUNITY_POST_NOT_FOUND));}
    private User user(Long id){return users.findById(id).orElseThrow(()->new CustomException(ErrorCode.USER_NOT_FOUND));}
    private void owner(Long actual,Long expected){if(!expected.equals(actual))throw new CustomException(ErrorCode.COMMUNITY_FORBIDDEN);}
    private String preview(String s){return s.length()<=220?s:s.substring(0,220)+"…";}
    private Map<Long,Long> counts(List<CommunityPostLikeRepository.CountView> values){return values.stream().collect(Collectors.toMap(CommunityPostLikeRepository.CountView::getId,CommunityPostLikeRepository.CountView::getCount));}
    private Map<Long,Long> commentCounts(List<CommunityCommentRepository.CountView> values){return values.stream().collect(Collectors.toMap(CommunityCommentRepository.CountView::getId,CommunityCommentRepository.CountView::getCount));}
}
