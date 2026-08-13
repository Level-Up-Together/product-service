package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums;

/**
 * 약관 버전 상태.
 * DRAFT(임시저장) 상태에서만 수정/삭제 가능하며, PUBLISHED(게시됨) 전환은 단방향이다.
 * 게시된 버전은 유저 동의의 대상이 되므로 불변으로 유지한다. (LUT-364)
 */
public enum TermVersionStatus {
    DRAFT,
    PUBLISHED
}
