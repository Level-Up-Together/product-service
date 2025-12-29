package io.pinkspider.leveluptogethermvp.customerservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InquiryType {
    SUGGESTION("건의사항"),
    BUG_REPORT("버그 신고"),
    ACCOUNT("계정 문의"),
    PAYMENT("결제 문의"),
    OTHER("기타");

    private final String description;
}
