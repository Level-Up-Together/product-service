package io.pinkspider.leveluptogethermvp.bffservice.application;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.PracticeStreakDto;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.WidgetSummaryResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * LUT-775: 홈 화면 위젯 요약 — 연속 실천 일수 + 오늘 완료 여부 + 활성 문장.
 *
 * <p>"오늘 완료" 는 연속 실천의 마지막 실천일(유저 로컬, 미션 완료 시각 기준 — LUT-717)이 오늘인가로 본다. 연속 실천 조회가 실패하면 0 으로 덮지 않고
 * 오류를 그대로 낸다 — 위젯은 실패하면 마지막으로 받은 값을 그린다(LUT-777).
 */
@Slf4j
@Service
public class BffWidgetService {

    static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    private final GamificationQueryFacade gamificationQueryFacade;
    private final UserQueryFacade userQueryFacade;
    private final WidgetMessageService widgetMessageService;
    private final Clock clock;

    @Autowired
    public BffWidgetService(
            GamificationQueryFacade gamificationQueryFacade,
            UserQueryFacade userQueryFacade,
            WidgetMessageService widgetMessageService) {
        this(gamificationQueryFacade, userQueryFacade, widgetMessageService, Clock.systemUTC());
    }

    BffWidgetService(
            GamificationQueryFacade gamificationQueryFacade,
            UserQueryFacade userQueryFacade,
            WidgetMessageService widgetMessageService,
            Clock clock) {
        this.gamificationQueryFacade = gamificationQueryFacade;
        this.userQueryFacade = userQueryFacade;
        this.widgetMessageService = widgetMessageService;
        this.clock = clock;
    }

    public WidgetSummaryResponse getSummary(String userId, String language) {
        ZoneId zone = resolveZone(userId);
        PracticeStreakDto practice = gamificationQueryFacade.getPracticeStreak(userId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate last = practice.lastPracticeDate();
        // 타임존을 서쪽으로 바꾼 직후엔 마지막 실천일이 "내일" 일 수 있다 — 그것도 오늘 한 것으로 본다
        boolean completedToday = last != null && !last.isBefore(today);
        return new WidgetSummaryResponse(
                practice.currentDays(),
                completedToday,
                now.truncatedTo(ChronoUnit.SECONDS),
                zone.getId(),
                widgetMessageService.getActiveMessages(language));
    }

    /** 연속 실천 반영(UserStatsService)과 같은 규칙 — 없거나 잘못된 값이면 Asia/Seoul */
    private ZoneId resolveZone(String userId) {
        try {
            String timezone = userQueryFacade.getPreferredTimezone(userId);
            return ZoneId.of(timezone != null ? timezone : DEFAULT_TIMEZONE);
        } catch (Exception e) {
            log.debug("위젯 — 타임존 해석 실패, 기본값 사용: userId={}, error={}", userId, e.getMessage());
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }
}
