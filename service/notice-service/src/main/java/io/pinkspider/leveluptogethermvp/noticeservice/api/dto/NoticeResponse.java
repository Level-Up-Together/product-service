package io.pinkspider.leveluptogethermvp.noticeservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.noticeservice.domain.enums.NoticeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class NoticeResponse {

    private Long id;
    private String title;
    private String content;
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
