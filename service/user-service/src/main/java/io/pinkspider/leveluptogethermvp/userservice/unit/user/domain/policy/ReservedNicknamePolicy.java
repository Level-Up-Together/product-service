package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.policy;

import java.util.List;
import java.util.Locale;

/**
 * LUT-705: 쓸 수 없는 닉네임 — 탑 방문객 이름("방문객 4821", locale 별 Visitor / ビジター / زائر)을 사칭하지 못하게, 그 표기로 시작하는
 * 닉네임은 가입·변경 때 막는다(영문은 대소문자 무시, 앞뒤 공백 무시).
 */
public final class ReservedNicknamePolicy {

    static final List<String> RESERVED_PREFIXES = List.of("방문객", "visitor", "ビジター", "زائر");

    private ReservedNicknamePolicy() {}

    public static boolean isReserved(String nickname) {
        if (nickname == null) {
            return false;
        }
        String normalized = nickname.strip().toLowerCase(Locale.ROOT);
        return RESERVED_PREFIXES.stream().anyMatch(normalized::startsWith);
    }
}
