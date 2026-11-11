package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.FirstDayGroup;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.RetentionVerdictCode;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.VerdictComparison;
import java.time.LocalDate;
import java.util.List;

/**
 * LUT-779: 첫날 행동별 리텐션 — 어드민 리텐션 화면(LUT-780). 리텐션 값은 그 지표가 확정된(가입 후 N일이 지난) 유저만으로 낸 % 이고, 확정된 유저가
 * 없으면 null("–").
 *
 * @param rangeStart 조회한 가입일 시작(KST)
 * @param rangeEnd 조회한 가입일 끝(KST) — 첫날(24시간)이 모두 끝난 마지막 날(오늘 − 2일)
 * @param dataStartDate 접속 기록 시작일 — 그 전 가입자는 대상이 아니다(없으면 null)
 * @param signups 기간 가입자(테스트·운영자 제외, 탈퇴자 포함)
 * @param firstDayCompletionRate 첫날 미션 완료율(%) — 가입자가 없으면 null
 */
@JsonNaming(SnakeCaseStrategy.class)
public record FirstDayRetentionResponse(
        String range,
        String platform,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        LocalDate dataStartDate,
        int signups,
        Double firstDayCompletionRate,
        List<GroupStat> groups,
        List<Verdict> verdicts) {

    /** 그룹 1개 — dN 은 % (소수 한 자리), dNUsers 는 판정 가능 인원 */
    @JsonNaming(SnakeCaseStrategy.class)
    public record GroupStat(
            FirstDayGroup group,
            int users,
            Double d1,
            int d1Users,
            Double d7,
            int d7Users,
            Double d30,
            int d30Users) {}

    /** 판정 1개 (D7) — diffPp 는 %p(뒤 그룹 − 앞 그룹, 소수 한 자리), 비교 그룹 중 0명이 있으면 null */
    @JsonNaming(SnakeCaseStrategy.class)
    public record Verdict(
            VerdictComparison comparison,
            RetentionVerdictCode code,
            Double diffPp,
            boolean significant) {}
}
