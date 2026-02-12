package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildJoinRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.JoinRequestStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuildJoinRequestResponse {

    private Long id;
    private Long guildId;
    private String guildName;
    private String requesterId;
    private String message;
    private JoinRequestStatus status;
    private String processedBy;
    private LocalDateTime processedAt;
    private String rejectReason;
    private LocalDateTime createdAt;

    /**
     * 가입 완료 후 멤버 여부 (APPROVED 상태일 때 true)
     */
    private Boolean isMember;

    /**
     * 가입 완료 후 업데이트된 현재 멤버 수
     */
    private Integer currentMemberCount;

    public static GuildJoinRequestResponse from(GuildJoinRequest request) {
        return GuildJoinRequestResponse.builder()
            .id(request.getId())
            .guildId(request.getGuild().getId())
            .guildName(request.getGuild().getName())
            .requesterId(request.getRequesterId())
            .message(request.getMessage())
            .status(request.getStatus())
            .processedBy(request.getProcessedBy())
            .processedAt(request.getProcessedAt())
            .rejectReason(request.getRejectReason())
            .createdAt(request.getCreatedAt())
            .build();
    }

    /**
     * 가입 완료 시 멤버 정보 포함하여 생성
     */
    public static GuildJoinRequestResponse fromWithMemberInfo(GuildJoinRequest request, boolean isMember, int currentMemberCount) {
        return GuildJoinRequestResponse.builder()
            .id(request.getId())
            .guildId(request.getGuild().getId())
            .guildName(request.getGuild().getName())
            .requesterId(request.getRequesterId())
            .message(request.getMessage())
            .status(request.getStatus())
            .processedBy(request.getProcessedBy())
            .processedAt(request.getProcessedAt())
            .rejectReason(request.getRejectReason())
            .createdAt(request.getCreatedAt())
            .isMember(isMember)
            .currentMemberCount(currentMemberCount)
            .build();
    }

    /**
     * OPEN 길드 즉시 가입 시 사용 (JoinRequest 없이 직접 생성)
     */
    public static GuildJoinRequestResponse forImmediateJoin(Long guildId, String guildName, String requesterId, int currentMemberCount) {
        return GuildJoinRequestResponse.builder()
            .guildId(guildId)
            .guildName(guildName)
            .requesterId(requesterId)
            .status(JoinRequestStatus.APPROVED)
            .isMember(true)
            .currentMemberCount(currentMemberCount)
            .createdAt(LocalDateTime.now())
            .build();
    }
}
