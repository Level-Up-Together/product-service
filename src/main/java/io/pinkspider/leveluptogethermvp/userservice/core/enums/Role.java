package io.pinkspider.leveluptogethermvp.userservice.core.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    ADMIN("admin", "관리자"),
    USER("user", "사용자");

    private final String key;
    private final String title;
}
