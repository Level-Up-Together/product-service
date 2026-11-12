package io.pinkspider.leveluptogethermvp.bffservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.PracticeStreakDto;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.WidgetSummaryResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageService;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageView;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BffWidgetService — 홈 화면 위젯 요약 (LUT-775)")
class BffWidgetServiceTest {

    private static final String USER_ID = "user-1";

    /** 2026-11-11 14:59:59.5 UTC = 서울 23:59:59 (11일) */
    private static final Instant BEFORE_SEOUL_MIDNIGHT = Instant.parse("2026-11-11T14:59:59.500Z");

    /** 2026-11-11 15:00 UTC = 서울 12일 00:00 */
    private static final Instant SEOUL_MIDNIGHT = Instant.parse("2026-11-11T15:00:00Z");

    @Mock GamificationQueryFacade gamificationQueryFacade;
    @Mock UserQueryFacade userQueryFacade;
    @Mock WidgetMessageService widgetMessageService;

    private final List<WidgetMessageView> messages =
            List.of(new WidgetMessageView(1L, 6, 12, WidgetMessageCondition.NOT_DONE, "문장", null));

    @BeforeEach
    void setUp() {
        // 일부 테스트가 덮어쓰거나 조회 전에 실패한다
        lenient()
                .when(gamificationQueryFacade.getPracticeStreak(USER_ID))
                .thenReturn(new PracticeStreakDto(12, 20, LocalDate.of(2026, 11, 11), 1, null));
        lenient().when(widgetMessageService.getActiveMessages("ko")).thenReturn(messages);
    }

    private BffWidgetService service(Instant now) {
        return new BffWidgetService(
                gamificationQueryFacade,
                userQueryFacade,
                widgetMessageService,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("연속 일수·문장을 그대로, 마지막 실천일이 유저 로컬 오늘이면 완료")
    void completedToday() {
        when(userQueryFacade.getPreferredTimezone(USER_ID)).thenReturn("Asia/Seoul");

        WidgetSummaryResponse response = service(BEFORE_SEOUL_MIDNIGHT).getSummary(USER_ID, "ko");

        assertThat(response.practiceStreakDays()).isEqualTo(12);
        assertThat(response.completedToday()).isTrue();
        assertThat(response.timezone()).isEqualTo("Asia/Seoul");
        assertThat(response.serverTime()).isEqualTo(Instant.parse("2026-11-11T14:59:59Z"));
        assertThat(response.messages()).isEqualTo(messages);
    }

    @Test
    @DisplayName("유저 타임존 자정이 지나면 완료가 false 로 바뀐다")
    void resetsAtUserMidnight() {
        when(userQueryFacade.getPreferredTimezone(USER_ID)).thenReturn("Asia/Seoul");

        assertThat(service(SEOUL_MIDNIGHT).getSummary(USER_ID, "ko").completedToday()).isFalse();
    }

    @Test
    @DisplayName("같은 시각이라도 타임존이 다르면 다른 날로 본다")
    void otherTimezone() {
        // 서울 자정 = UTC 15시 → UTC 유저에겐 아직 11일
        when(userQueryFacade.getPreferredTimezone(USER_ID)).thenReturn("UTC");

        WidgetSummaryResponse response = service(SEOUL_MIDNIGHT).getSummary(USER_ID, "ko");

        assertThat(response.completedToday()).isTrue();
        assertThat(response.timezone()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("마지막 실천일이 유저 로컬 '내일'(서쪽으로 타임존 변경 직후)이어도 완료")
    void lastPracticeAfterToday() {
        // 서울 12일 00:30 에 완료 → 마지막 실천일 12일, 유저가 LA 로 바꾸면 LA 는 아직 11일
        when(gamificationQueryFacade.getPracticeStreak(USER_ID))
                .thenReturn(new PracticeStreakDto(13, 20, LocalDate.of(2026, 11, 12)));
        when(userQueryFacade.getPreferredTimezone(USER_ID)).thenReturn("America/Los_Angeles");

        assertThat(service(SEOUL_MIDNIGHT).getSummary(USER_ID, "ko").completedToday()).isTrue();
    }

    @Test
    @DisplayName("실천 이력이 없으면 0일·미완료")
    void noPractice() {
        when(gamificationQueryFacade.getPracticeStreak(USER_ID))
                .thenReturn(PracticeStreakDto.empty());
        when(userQueryFacade.getPreferredTimezone(USER_ID)).thenReturn("Asia/Seoul");

        WidgetSummaryResponse response = service(BEFORE_SEOUL_MIDNIGHT).getSummary(USER_ID, "ko");

        assertThat(response.practiceStreakDays()).isZero();
        assertThat(response.completedToday()).isFalse();
    }

    @Test
    @DisplayName("타임존이 없으면 Asia/Seoul")
    void nullTimezone() {
        when(userQueryFacade.getPreferredTimezone(USER_ID)).thenReturn(null);

        assertThat(service(BEFORE_SEOUL_MIDNIGHT).getSummary(USER_ID, "ko").timezone())
                .isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("타임존 값이 잘못됐거나 조회가 실패하면 Asia/Seoul")
    void invalidTimezone() {
        when(userQueryFacade.getPreferredTimezone(USER_ID)).thenReturn("Mars/Olympus");

        assertThat(service(BEFORE_SEOUL_MIDNIGHT).getSummary(USER_ID, "ko").timezone())
                .isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("연속 실천 조회가 실패하면 0 으로 덮지 않고 오류를 낸다(위젯은 마지막 값 유지)")
    void practiceFailure() {
        when(userQueryFacade.getPreferredTimezone(USER_ID)).thenReturn("Asia/Seoul");
        when(gamificationQueryFacade.getPracticeStreak(USER_ID))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service(BEFORE_SEOUL_MIDNIGHT).getSummary(USER_ID, "ko"))
                .isInstanceOf(IllegalStateException.class);
    }
}
