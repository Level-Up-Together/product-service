package io.pinkspider.leveluptogethermvp.bffservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageView;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetStreakColorView;
import java.time.Instant;
import java.util.List;

/**
 * LUT-775: 홈 화면 위젯 요약.
 *
 * @param practiceStreakDays 연속 실천 일수 — 프로필 {@code practice_streak_days} 와 같은 값(방어권 반영)
 * @param completedToday 유저 타임존 기준 오늘 미션을 하나라도 완료했는가
 * @param serverTime 응답 시각(UTC)
 * @param timezone 판정에 쓴 유저 타임존({@code users.preferred_timezone}, 없으면 Asia/Seoul)
 * @param messages 활성 문장 전체(요청 언어) — 위젯이 시간대·완료 여부로 직접 고른다
 * @param streakColors LUT-816: 'N일' 숫자 색 구간 전체(시작 일수 순) — 위젯이 연속 실천 일수가 드는 구간의 색으로 숫자를 그린다. 드는 구간이
 *     없으면(빈 목록 포함) 위젯 기본 색. 추가 필드라 이 값을 모르는 구앱 위젯은 무시한다
 */
@JsonNaming(SnakeCaseStrategy.class)
public record WidgetSummaryResponse(
        int practiceStreakDays,
        boolean completedToday,
        Instant serverTime,
        String timezone,
        List<WidgetMessageView> messages,
        List<WidgetStreakColorView> streakColors) {}
