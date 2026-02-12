package io.pinkspider.leveluptogethermvp.profanity.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProfanitySeverity {
    LOW("낮음", "경미한 수준"),
    MEDIUM("중간", "중간 수준"),
    HIGH("높음", "심각한 수준");

    private final String name;
    private final String description;
}
