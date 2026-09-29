package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** LUT-528: 장착 아이템 푸시 발송 설정 등록/수정 요청 (어드민 → 내부 API) */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class ItemPushSettingRequest {

    /** 발송 시각 HH:mm — 유저 로컬 기준, 08:00~20:59 만 허용 */
    @NotBlank(message = "발송 시각은 필수입니다.")
    @Pattern(
            regexp = "^(0[89]|1\\d|20):[0-5]\\d$",
            message = "발송 시각은 08:00~20:59 사이 HH:mm 형식이어야 합니다.")
    private String sendTime;
}
