package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.policy;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** LUT-779: 가입 첫날 = 가입 시각부터 24시간 {@code [가입, 가입 + 24h)} (UTC). 날짜가 아니라 시각 기준이다 */
public final class FirstDayWindow {

    public static final Duration LENGTH = Duration.ofHours(24);

    private FirstDayWindow() {}

    public static boolean contains(LocalDateTime signupAt, LocalDateTime at) {
        return at != null && !at.isBefore(signupAt) && at.isBefore(signupAt.plus(LENGTH));
    }

    /** 이벤트가 자기 첫날 안에 있었던 유저들 — 대상(signupAt 의 키)이 아닌 유저의 이벤트는 무시한다 */
    public static Set<String> usersWithin(
            Map<String, LocalDateTime> signupAt, Collection<UserEvent> events) {
        Set<String> users = new HashSet<>();
        for (UserEvent event : events) {
            LocalDateTime signup = signupAt.get(event.userId());
            if (signup != null && contains(signup, event.at())) {
                users.add(event.userId());
            }
        }
        return users;
    }
}
