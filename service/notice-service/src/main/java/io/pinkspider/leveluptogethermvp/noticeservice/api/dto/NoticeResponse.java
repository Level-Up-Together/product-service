package io.pinkspider.leveluptogethermvp.noticeservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.noticeservice.domain.enums.NoticeType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class NoticeResponse {

    private Long id;
    private String title;

    /** LUT-128: 제목 다국어 (없으면 null — 클라이언트가 한국어 제목으로 대신) */
    private String titleEn;

    private String titleAr;
    private String titleJa;
    private String content;
    private String contentEn;
    private String contentAr;
    private String contentJa;
    private NoticeType noticeType;
    private String noticeTypeName;
    private Integer priority;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isActive;
    private Boolean isPopup;
    private String createdBy;
    private String modifiedBy;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
