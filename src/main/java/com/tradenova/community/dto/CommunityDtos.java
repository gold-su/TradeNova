package com.tradenova.community.dto;

import com.tradenova.community.entity.CommunityReport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class CommunityDtos {
    private CommunityDtos() {}
    public record PostRequest(@NotBlank @Size(max=120) String title, @NotBlank @Size(max=10000) String content) {}
    public record CommentRequest(@NotBlank @Size(max=2000) String content) {}
    public record ReportRequest(@NotNull CommunityReport.TargetType targetType, @NotNull Long targetId,
                                @NotNull CommunityReport.ReportReason reason) {}
    public record Author(Long userId, String nickname, int level, long completedTrainingCount) {}
    public record PostSummary(Long id, String title, String contentPreview, Author author, long likeCount,
                              long commentCount, boolean likedByMe, OffsetDateTime createdAt) {}
    public record PostDetail(Long id, String title, String content, Author author, long likeCount,
                             long commentCount, boolean likedByMe, boolean mine, OffsetDateTime createdAt,
                             OffsetDateTime updatedAt) {}
    public record Comment(Long id, Author author, String content, boolean mine, OffsetDateTime createdAt) {}
    public record Like(boolean likedByMe, long likeCount) {}
    public record Report(Long id) {}
}
