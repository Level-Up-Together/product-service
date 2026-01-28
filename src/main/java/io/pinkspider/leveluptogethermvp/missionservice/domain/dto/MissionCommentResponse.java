package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionComment;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MissionCommentResponse {

    private Long id;
    private Long missionId;
    private String userId;
    private String userNickname;
    private String userProfileImageUrl;
    private Integer userLevel;
    private String content;

    @JsonIgnore
    private boolean isDeleted;

    @JsonIgnore
    private boolean isMyComment;

    @JsonGetter("is_deleted")
    public boolean getIsDeleted() {
        return isDeleted;
    }

    @JsonGetter("is_my_comment")
    public boolean getIsMyComment() {
        return isMyComment;
    }

    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static MissionCommentResponse from(MissionComment comment) {
        return from(comment, null);
    }

    public static MissionCommentResponse from(MissionComment comment, String currentUserId) {
        return MissionCommentResponse.builder()
            .id(comment.getId())
            .missionId(comment.getMission().getId())
            .userId(comment.getUserId())
            .userNickname(comment.getUserNickname())
            .userProfileImageUrl(comment.getUserProfileImageUrl())
            .userLevel(comment.getUserLevel())
            .content(comment.getContent())
            .isDeleted(comment.getIsDeleted())
            .isMyComment(currentUserId != null && currentUserId.equals(comment.getUserId()))
            .createdAt(comment.getCreatedAt())
            .modifiedAt(comment.getModifiedAt())
            .build();
    }
}
