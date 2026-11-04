package io.pinkspider.leveluptogethermvp.towerservice.domain.enums;

import java.util.Optional;

/** LUT-635: 어드민이 올리는 층 파일 종류 — 로컬 저장 폴더·S3 키 접두어가 갈린다. BGM(LUT-637)은 이미지가 아니라 오디오 검증을 탄다. */
public enum TowerImageKind {
    BACKGROUND("background"),
    MASK("mask"),
    /** LUT-637: 배경 음악 (mp3·aac·m4a, 10MB) — 이미지 업로드 API 로는 못 올린다 */
    BGM("bgm"),
    /** LUT-693: NPC 프로필 이미지 (정사각 권장, jpg·png·webp) — 층 이미지 업로드 API {@code kind=NPC} */
    NPC("npc");

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
