package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.lang.Nullable;

/**
 * LUT-705: 방문객 세션의 공개 식별자 — 방문객 키(본인 확인용 비밀)를 그대로 내리지 않고 해시 앞 16자리만 내린다. 같은 세션이면 층을 옮기거나 다시 들어와도
 * 같아서, 로그인 유저가 방문객을 숨기면 그 세션 동안 계속 숨길 수 있다(점유자 ID 는 입장마다 바뀐다).
 */
public final class TowerVisitorTag {
    private static final int LENGTH = 16;

    private TowerVisitorTag() {}

    @Nullable
    public static String of(@Nullable String visitorKey) {
        if (visitorKey == null || visitorKey.isBlank()) {
            return null;
        }
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(
                                    ("tower-visitor:" + visitorKey)
                                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
