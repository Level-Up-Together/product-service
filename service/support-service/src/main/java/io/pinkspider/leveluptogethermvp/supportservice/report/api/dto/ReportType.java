package io.pinkspider.leveluptogethermvp.supportservice.report.api.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportType {
    INAPPROPRIATE_IMAGE("부적절한 이미지"),
    SPAM("스팸/광고"),
    HARASSMENT("괴롭힘/따돌림"),
    HATE_SPEECH("혐오 발언"),
    IMPERSONATION("사칭"),
    VIOLENCE("폭력적 콘텐츠"),
    OTHER("기타");

    private final String description;
}
