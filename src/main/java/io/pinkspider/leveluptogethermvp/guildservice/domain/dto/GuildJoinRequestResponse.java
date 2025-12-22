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
}
