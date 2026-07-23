package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionParticipationType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MissionResponse {

    private Long id;
    private String title;
    private String titleEn;
    private String titleAr;
    private String titleJa;
    private String description;
    private String descriptionEn;
    private String descriptionAr;
    private String descriptionJa;
    private MissionStatus status;
    private MissionVisibility visibility;
    private MissionType type;
    private MissionSource source;
    private MissionParticipationType participationType;
    private Boolean isCustomizable;
    private Boolean isPinned;
    private MissionExecutionMode executionMode;
    private String creatorId;
    private String guildId;
    private String guildName;
    private Integer maxParticipants;
    private Integer currentParticipants;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private MissionInterval missionInterval;
    private Integer durationDays;
    private Integer durationMinutes;
    private Integer expPerCompletion;
    private Integer bonusExpOnFullCompletion;
    private Integer targetDurationMinutes;
    private Integer dailyExecutionLimit;

    // 카테고리 정보
    private Long categoryId;
    private String categoryName;

    private LocalDateTime createdAt;

    private LocalDateTime modifiedAt;

    // 신고 처리중 여부
    private Boolean isUnderReview;

    /** QA-176: 미션 누적 EXP (historic 합산, 탈퇴/실패 참여자 기여도 유지). 응답 시점에 채워진다. */
    private Integer totalExpEarned;

    /**
     * QA-192: 마스터에 의해 삭제(소프트 삭제)된 길드 미션 여부. 이미 수락/수행 중인 참여자에게는
     * 마지막 인증까지 노출되지만, 프론트는 이 플래그(또는 status == COMPLETED/CANCELLED)로
     * "삭제/종료된 미션" 안내를 표시한다. 기존 응답 호환을 위해 true일 때만 직렬화한다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean isDeleted;

    public static MissionResponse from(Mission mission) {
        return from(mission, (String) null);
    }

    /**
     * LUT-255: 대표 필드(title/description)를 locale에 맞게 채운다 (locale null이면 한국어 기본값).
     * raw 다국어 필드(title_en 등)는 그대로 유지. categoryName localize는 meta 조회가 필요해
     * 서비스 레이어에서 덮어쓴다 (denormalized 한국어 이름은 fallback).
     */
    public static MissionResponse from(Mission mission, String locale) {
        return MissionResponse.builder()
            .id(mission.getId())
            .title(mission.getLocalizedTitle(locale))
            .titleEn(mission.getTitleEn())
            .titleAr(mission.getTitleAr())
            .titleJa(mission.getTitleJa())
            .description(mission.getLocalizedDescription(locale))
            .descriptionEn(mission.getDescriptionEn())
            .descriptionAr(mission.getDescriptionAr())
            .descriptionJa(mission.getDescriptionJa())
            .status(mission.getStatus())
            .visibility(mission.getVisibility())
            .type(mission.getType())
            .source(mission.getSource())
            .participationType(mission.getParticipationType())
            .isCustomizable(mission.getIsCustomizable())
            .isPinned(mission.getIsPinned())
            .executionMode(mission.getExecutionMode())
            .creatorId(mission.getCreatorId())
            .guildId(mission.getGuildId())
            .guildName(mission.getGuildName())
            .maxParticipants(mission.getMaxParticipants())
            .startAt(mission.getStartAt())
            .endAt(mission.getEndAt())
            .missionInterval(mission.getMissionInterval())
            .durationDays(mission.getDurationDays())
            .durationMinutes(mission.getDurationMinutes())
            .expPerCompletion(mission.getExpPerCompletion())
            .bonusExpOnFullCompletion(mission.getBonusExpOnFullCompletion())
            .targetDurationMinutes(mission.getTargetDurationMinutes())
            .dailyExecutionLimit(mission.getDailyExecutionLimit())
            .categoryId(mission.getCategoryId())
            .categoryName(mission.getCategoryName())
            .createdAt(mission.getCreatedAt())
            .modifiedAt(mission.getModifiedAt())
            .isDeleted(Boolean.TRUE.equals(mission.getIsDeleted()) ? Boolean.TRUE : null)
            .build();
    }

    public static MissionResponse from(Mission mission, int currentParticipants) {
        MissionResponse response = from(mission);
        response.setCurrentParticipants(currentParticipants);
        return response;
    }

    /** LUT-255: locale 버전 */
    public static MissionResponse from(Mission mission, int currentParticipants, String locale) {
        MissionResponse response = from(mission, locale);
        response.setCurrentParticipants(currentParticipants);
        return response;
    }
}
