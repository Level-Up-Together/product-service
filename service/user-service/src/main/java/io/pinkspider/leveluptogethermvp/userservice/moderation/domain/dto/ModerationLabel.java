package io.pinkspider.leveluptogethermvp.userservice.moderation.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이미지 분석에서 감지된 개별 레이블 정보
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationLabel {

    /**
     * 감지된 카테고리명 (예: "Explicit Nudity", "Violence")
     */
    private String category;

    /**
     * 세부 레이블명 (예: "Nudity", "Graphic Violence")
     */
    private String name;

    /**
     * 감지 신뢰도 (0.0 ~ 100.0)
     */
    private double confidence;

    /**
     * 부모 카테고리명 (있는 경우)
     */
    private String parentName;
}
