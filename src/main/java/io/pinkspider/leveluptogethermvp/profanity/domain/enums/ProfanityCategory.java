package io.pinkspider.leveluptogethermvp.profanity.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProfanityCategory {
    GENERAL("일반", "일반적인 욕설"),
    SEXUAL("성적", "성적인 욕설"),
    DISCRIMINATION("차별", "차별적 표현"),
    VIOLENCE("폭력", "폭력적 표현"),
    POLITICS("정치", "정치적 표현");

    private final String name;
    private final String description;
}
