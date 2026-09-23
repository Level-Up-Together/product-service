package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** LUT-516: 장착 아이템 개별 푸시 메시지 생성/수정 요청 (어드민 → 내부 API) */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class ItemPushMessageRequest {

    @NotBlank(message = "메시지(기본)는 필수입니다.")
    @Size(max = 500, message = "메시지는 500자 이내여야 합니다.")
    private String message;

    @Size(max = 500, message = "영어 메시지는 500자 이내여야 합니다.")
    private String messageEn;

    @Size(max = 500, message = "아랍어 메시지는 500자 이내여야 합니다.")
    private String messageAr;

    @Size(max = 500, message = "일본어 메시지는 500자 이내여야 합니다.")
    private String messageJa;

    @NotBlank(message = "발송 시각은 필수입니다.")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "발송 시각은 HH:mm 형식이어야 합니다.")
    private String sendTime;

    /** 미지정 시 활성(true)으로 생성 */
    private Boolean enabled;
}
