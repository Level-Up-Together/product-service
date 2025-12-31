package io.pinkspider.leveluptogethermvp.supportservice.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문의 유형 옵션 DTO
 * 프론트엔드 select/radio 버튼에 표시할 value와 label을 포함
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryTypeOption {

    @JsonProperty("value")
    private String value;

    @JsonProperty("label")
    private String label;

    /**
     * InquiryType enum에서 InquiryTypeOption 생성
     */
    public static InquiryTypeOption from(InquiryType type) {
        return InquiryTypeOption.builder()
            .value(type.name())
            .label(type.getDescription())
            .build();
    }
}
