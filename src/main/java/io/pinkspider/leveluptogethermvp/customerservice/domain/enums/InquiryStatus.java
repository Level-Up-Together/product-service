package io.pinkspider.leveluptogethermvp.customerservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InquiryStatus {
    PENDING("대기중"),
    IN_PROGRESS("처리중"),
    RESOLVED("해결됨"),
    CLOSED("종료");

    private final String description;
}
