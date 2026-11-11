package io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.util.List;

/**
 * LUT-790: 미션 통계 요약 — 어드민 "미션관리 > 미션 통계"(LUT-791). 날짜는 전부 KST, 테스트·운영자 제외는 KPI 미션 완료 수와 같다.
 *
 * <p>완료는 두 기준이 있다. {@code completions}·{@code daily[].completed} 는 <b>완료 시각</b> 기준(KPI {@code
 * mission_completions} 와 같은 숫자). 완료율은 <b>예정일</b> 기준 — 예정일이 기간 안인 수행 중 완료한 비율(월간 리포트와 같은 정의)이라
 * 분자·분모가 같은 수행이고 100% 를 넘지 않는다. 자정을 넘겨 끝낸 수행 때문에 두 완료 수는 조금 다를 수 있다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record MissionStatsSummaryResponse(
        LocalDate from,
        LocalDate to,
        Created created,
        Completions completions,
        CompletionRate completionRate,
        List<Daily> daily,
        List<Breakdown> byType,
        List<Breakdown> byMode,
        List<Breakdown> byCategory) {

    /**
     * 기간에 만든 미션(미션북에서 가져온 미션·길드 미션 포함, 어드민이 직접 만든 시스템 미션 제외).
     *
     * @param deleted 그중 지금은 지운 미션 — total 에 포함
     * @param regular total − pinned
     */
    @JsonNaming(SnakeCaseStrategy.class)
    public record Created(int total, int deleted, int pinned, int regular) {}

    /**
     * 완료 시각 기준 완료.
     *
     * @param autoCompleted 그중 4시간 자동 종료
     * @param users 완료한 유저 수(기간 전체에서 중복 제거)
     * @param avgPerUser 1인당 평균 완료 수, users 0 이면 null
     */
    @JsonNaming(SnakeCaseStrategy.class)
    public record Completions(int total, int autoCompleted, int users, Double avgPerUser) {}

    /**
     * 예정일 기준 완료율.
     *
     * @param scheduled 예정 수행 수
     * @param completed 그중 완료
     * @param rate completed ÷ scheduled (%), 예정 0 이면 null
     */
    @JsonNaming(SnakeCaseStrategy.class)
    public record CompletionRate(int scheduled, int completed, Double rate) {}

    /**
     * 하루 1행 — 기간의 모든 날짜(0 포함).
     *
     * @param completed 완료 시각 기준 완료 수
     * @param scheduledCompleted 예정일 기준 완료 수(완료율의 분자)
     */
    @JsonNaming(SnakeCaseStrategy.class)
    public record Daily(
            LocalDate date,
            int created,
            int completed,
            int scheduled,
            int scheduledCompleted,
            Double completionRate) {}

    /**
     * 분류 1행 — 종류(PERSONAL·GUILD·MISSION_BOOK) · 수행 방식(TIMED·SIMPLE) · 카테고리.
     *
     * @param key 종류·수행 방식 이름, 카테고리는 ID 문자열(카테고리 없음 = null)
     * @param name 카테고리 이름(종류·수행 방식은 null — 화면이 번역), 지워진 카테고리도 null
     * @param nameEn 카테고리 영어 이름
     */
    @JsonNaming(SnakeCaseStrategy.class)
    public record Breakdown(
            String key,
            String name,
            String nameEn,
            int created,
            int completed,
            int scheduled,
            int scheduledCompleted,
            Double completionRate) {}
}
