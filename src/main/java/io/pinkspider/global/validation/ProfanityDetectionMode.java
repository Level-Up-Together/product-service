package io.pinkspider.global.validation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 비속어 탐지 모드
 */
@Getter
@RequiredArgsConstructor
public enum ProfanityDetectionMode {

    /**
     * 엄격 모드: 초성 검사 + 레벤슈타인 거리 유사도 검사 포함
     */
    STRICT("엄격", "초성 검사 + 유사도 검사 포함"),

    /**
     * 일반 모드 (기본): 대소문자 무시 + 공백/특수문자 제거 + 초성 검사
     */
    NORMAL("일반", "대소문자 무시 + 공백/특수문자 제거 + 초성 검사"),

    /**
     * 완화 모드: 단순 포함 여부만 검사
     */
    LENIENT("완화", "단순 포함 여부만 검사");

    private final String name;
    private final String description;
}
