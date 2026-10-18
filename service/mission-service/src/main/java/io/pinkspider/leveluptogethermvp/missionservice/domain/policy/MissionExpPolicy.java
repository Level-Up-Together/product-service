package io.pinkspider.leveluptogethermvp.missionservice.domain.policy;

import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.MissionCategory;
import java.util.List;

/**
 * LUT-568: 미션 완료 경험치 공식 (단일 출처).
 *
 * <ul>
 *   <li>체크(SIMPLE): 완료당 {@link #SIMPLE_EXP}, 하루 {@link #SIMPLE_DAILY_LIMIT}회까지만 지급 (초과분은 0)
 *   <li>타이머(TIMED): {@link #TIMED_FULL_RATE_MINUTES}분까지 분당 1, 초과 구간은 {@link
 *       #TIMED_REDUCED_RATE_MINUTES_PER_EXP}분당 1 (예: 240분 → 60 + 36 = 96). 완료 기본 경험치 없음 — 쪼개기 유인 방지
 *   <li>체크 일일 마일스톤: 당일 체크 완료 {@link #SIMPLE_DAILY_MILESTONES}개째마다 {@link #MILESTONE_BONUS_EXP} (각
 *       1회, 한도 초과로 0 처리된 완료도 횟수에 포함)
 * </ul>
 *
 * <p>출처(개인·길드·미션북) 무관하게 {@code execution_mode} 기준으로만 적용한다. 미션북 완주 보너스({@code
 * bonusExpOnFullCompletion})는 별도 가산. 프론트는 서버가 내려준 값을 표시만 하므로 공식을 재구현하지 말 것.
 */
public final class MissionExpPolicy {

    /** 체크 미션 완료당 경험치 (LUT-568: 5 → 10). */
    public static final int SIMPLE_EXP = 10;

    /** 체크 미션 하루 경험치 지급 한도 횟수 (QA-130). */
    public static final int SIMPLE_DAILY_LIMIT = 10;

    /** 타이머 미션이 분당 1 EXP 를 받는 상한(분). */
    public static final int TIMED_FULL_RATE_MINUTES = 60;

    /** 상한 초과 구간의 환산 단위(분당 0.2 = 5분당 1). */
    public static final int TIMED_REDUCED_RATE_MINUTES_PER_EXP = 5;

    /** 목표시간 없는 레거시 타이머 미션의 경험치 산정 상한(분). */
    public static final int TIMED_MAX_MINUTES = 480;

    /** 스케줄러 자동 종료 상한(분) — {@code MissionExecutionLifecycle.MAXIMUM_EXECUTION_MINUTES} 와 동일. */
    public static final int AUTO_COMPLETE_MINUTES = 240;

    /** 체크 일일 마일스톤 달성 수 (오름차순). */
    public static final List<Integer> SIMPLE_DAILY_MILESTONES = List.of(3, 5);

    /** 마일스톤 1회당 보너스 경험치. */
    public static final int MILESTONE_BONUS_EXP = 20;

    /** 마일스톤 보너스 경험치 이력의 카테고리명 — 출석 보상과 같이 기본 카테고리로 기록해 일별 합계·MVP 집계에 포함시킨다. */
    public static final String MILESTONE_CATEGORY_NAME = MissionCategory.DEFAULT_CATEGORY_NAME;

    private MissionExpPolicy() {}

    /**
     * 타이머 미션 경험치. 1분 미만도 최소 1, {@link #TIMED_MAX_MINUTES} 초과분은 무시.
     *
     * @param minutes 수행(또는 목표) 분
     */
    public static int timedExp(long minutes) {
        long capped = Math.max(0, Math.min(minutes, TIMED_MAX_MINUTES));
        long fullRate = Math.min(capped, TIMED_FULL_RATE_MINUTES);
        long reducedRate =
                Math.max(0, capped - TIMED_FULL_RATE_MINUTES) / TIMED_REDUCED_RATE_MINUTES_PER_EXP;
        return (int) Math.max(1, fullRate + reducedRate);
    }

    /**
     * 당일 체크 완료 누계가 마일스톤에 정확히 도달했으면 그 마일스톤 수, 아니면 0.
     *
     * @param simpleCompletionsToday 이번 완료를 포함한 당일 체크 완료 수
     */
    public static int milestoneReached(long simpleCompletionsToday) {
        for (int milestone : SIMPLE_DAILY_MILESTONES) {
            if (simpleCompletionsToday == milestone) {
                return milestone;
            }
        }
        return 0;
    }

    /**
     * 스케줄러 자동 종료(4시간 초과, 목표시간 없는 레거시 미션) 경험치. 설정값({@code mission.execution.base-exp})을 쓰되 정상적으로
     * 4시간을 채운 완료({@code timedExp(240)})보다 많이 줄 수는 없다 — 방치가 정상 완료보다 유리해지는 어뷰징 경로를 막는다.
     */
    public static int autoCompleteExp(int configuredBaseExp) {
        return Math.min(configuredBaseExp, timedExp(AUTO_COMPLETE_MINUTES));
    }
}
