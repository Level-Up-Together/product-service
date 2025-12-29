package io.pinkspider.leveluptogethermvp.supportservice.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InquiryType {
    ACCOUNT("계정 관련"),
    PAYMENT("결제/환불"),
    BUG("버그 신고"),
    SUGGESTION("건의사항"),
    GUILD("길드 관련"),
    MISSION("미션 관련"),
    OTHER("기타");

    private final String description;

    @JsonValue
    public String getValue() {
        return name();
    }
}
