package com.tradenova.community.service;

import com.tradenova.community.dto.CommunityDtos.*;
import com.tradenova.community.entity.*;
import com.tradenova.community.repository.*;
import com.tradenova.common.exception.CustomException;
import com.tradenova.user.entity.User;
import com.tradenova.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommunityServiceTest {
    private final CommunityPostRepository posts=mock(CommunityPostRepository.class);
    private final CommunityCommentRepository comments=mock(CommunityCommentRepository.class);
    private final CommunityPostLikeRepository likes=mock(CommunityPostLikeRepository.class);
    private final CommunityReportRepository reports=mock(CommunityReportRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final CommunityProfileService profiles=mock(CommunityProfileService.class);
    private final CommunityService service=new CommunityService(posts,comments,likes,reports,users,profiles);

    @Test void nonOwnerCannotUpdateOrDeletePost(){CommunityPost p=post(1L,2L);when(posts.findDetailById(1L)).thenReturn(Optional.of(p));assertThrows(CustomException.class,()->service.update(9L,1L,new PostRequest("title","content")));assertThrows(CustomException.class,()->service.deletePost(9L,1L));verify(posts,never()).delete(any());}
    @Test void likeUsesDatabaseIdempotentInsert(){when(posts.findDetailById(1L)).thenReturn(Optional.of(post(1L,2L)));when(users.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).nickname("me").build()));when(likes.countByPostId(1L)).thenReturn(3L);Like result=service.like(7L,1L);assertTrue(result.likedByMe());assertEquals(3,result.likeCount());verify(likes).insertIgnore(1L,7L);}
    @Test void duplicateReportIsRejected(){when(posts.existsById(1L)).thenReturn(true);when(reports.existsByReporterIdAndTargetTypeAndTargetId(7L,CommunityReport.TargetType.POST,1L)).thenReturn(true);assertThrows(CustomException.class,()->service.report(7L,new ReportRequest(CommunityReport.TargetType.POST,1L,CommunityReport.ReportReason.SPAM)));}
    private CommunityPost post(Long id,Long userId){return CommunityPost.builder().id(id).user(User.builder().id(userId).nickname("nova").build()).title("title").content("content").build();}
}
