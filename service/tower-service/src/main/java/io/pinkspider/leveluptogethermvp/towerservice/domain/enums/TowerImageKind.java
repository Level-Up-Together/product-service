package io.pinkspider.leveluptogethermvp.towerservice.domain.enums;

import java.util.Optional;

/** LUT-635: 어드민이 올리는 층 이미지 종류 — 로컬 저장 폴더·S3 키 접두어가 갈린다. */
public enum TowerImageKind {
    BACKGROUND("background"),
    MASK("mask");

    private final String folder;

    TowerImageKind(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }

    /** 대소문자 무관 파싱. 비었거나 모르는 값은 empty — 컨트롤러가 400 150110 으로 바꾼다. */
    public static Optional<TowerImageKind> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        for (TowerImageKind kind : values()) {
            if (kind.name().equalsIgnoreCase(raw.trim())) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
