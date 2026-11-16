package io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.enums.CompletionBucket;
import java.time.LocalDate;
import java.util.List;

/**
 * LUT-790: 사용자별 수행 횟수 — 기간 안에 미션을 1번 이상 완료한 유저(완료 시각 기준, KPI 와 같은 제외 규칙).
 *
 * @param keyword 적용한 닉네임 검색어(없으면 null)
 * @param sort 적용한 정렬 {@code 열,asc|desc}
 * @param page 0부터
 * @param totalUsers 검색어 적용 뒤 유저 수
 * @param distribution 완료 수 분포 — 검색어와 상관없이 기간의 완료 유저 전체
 */
@JsonNaming(SnakeCaseStrategy.class)
public record MissionStatsUsersResponse(
        LocalDate from,
        LocalDate to,
        String keyword,
        String sort,
        int page,
        int size,
        int totalUsers,
        int totalPages,
        List<Bucket> distribution,
        List<UserRow> users) {

    /** 분포 1구간 — max 는 포함, 마지막 구간은 null */
    @JsonNaming(SnakeCaseStrategy.class)
    public record Bucket(CompletionBucket bucket, int min, Integer max, int users) {}

    /**
     * 유저 1행.
     *
     * @param nickname 탈퇴 유저는 익명화된 닉네임, 유저 행이 없으면 null
     * @param level 경험치 행이 없으면 null
     * @param timerMinutes 시간 측정 미션의 시작~완료 분 합
     * @param lastCompletedDate 기간 안 마지막 완료일(KST)
     */
    @JsonNaming(SnakeCaseStrategy.class)
    public record UserRow(
            String userId,
            String nickname,
            Integer level,
            boolean withdrawn,
            int completions,
            long timerMinutes,
            LocalDate lastCompletedDate) {}
}
