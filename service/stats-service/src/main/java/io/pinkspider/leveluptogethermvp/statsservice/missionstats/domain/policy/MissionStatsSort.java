package io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.policy;

import io.pinkspider.leveluptogethermvp.statsservice.api.exception.StatsInvalidParamException;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.enums.MissionStatsSortField;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * LUT-790: {@code sort=열[,asc|desc]} (Spring Pageable 과 같은 모양). 비우면 완료 수 내림차순. 허용값 밖이면 400 {@code
 * 100101}.
 */
public record MissionStatsSort(MissionStatsSortField field, boolean ascending) {

    public static final MissionStatsSort DEFAULT =
            new MissionStatsSort(MissionStatsSortField.COMPLETIONS, false);

    public static MissionStatsSort parse(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT;
        }
        String[] parts = sort.trim().split(",", -1);
        MissionStatsSortField field = MissionStatsSortField.fromParam(parts[0].trim());
        if (field == null || parts.length > 2) {
            throw invalid();
        }
        if (parts.length == 1) {
            return new MissionStatsSort(field, field.isDefaultAscending());
        }
        return switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> new MissionStatsSort(field, true);
            case "desc" -> new MissionStatsSort(field, false);
            default -> throw invalid();
        };
    }

    /** 응답에 다시 싣는 값 — {@code completions,desc} */
    public String value() {
        return field.getParam() + (ascending ? ",asc" : ",desc");
    }

    private static StatsInvalidParamException invalid() {
        String fields =
                Arrays.stream(MissionStatsSortField.values())
                        .map(MissionStatsSortField::getParam)
                        .collect(Collectors.joining("|"));
        return new StatsInvalidParamException("sort", fields + "[,asc|desc]");
    }
}
