package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LUT-528/573: 장착 아이템 푸시 발송 설정 등록/수정 요청 (어드민 → 내부 API). 두 시각 모두 선택이며 null 이면 그 슬롯은 발송하지 않는다. 둘 다
 * null 이면 정오 INACTIVE 슬롯을 포함해 전체 미발송(어드민 경고와 일치).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class ItemPushSettingRequest {

    /** 아침(활동 전) 슬롯 HH:mm — 유저 로컬 기준, 08:00~11:59 만 허용. null = 미발송 */
    @Pattern(
            regexp = "^(0[89]|1[01]):[0-5]\\d$",
            message = "아침 발송 시각은 08:00~11:59 사이 HH:mm 형식이어야 합니다.")
    private String beforeActivityTime;

    /** 오후(완료 후) 슬롯 HH:mm — 유저 로컬 기준, 12:00~20:59 만 허용. null = 미발송 */
    @Pattern(
            regexp = "^(1[2-9]|20):[0-5]\\d$",
            message = "오후 발송 시각은 12:00~20:59 사이 HH:mm 형식이어야 합니다.")
    private String afterCompleteTime;
}
