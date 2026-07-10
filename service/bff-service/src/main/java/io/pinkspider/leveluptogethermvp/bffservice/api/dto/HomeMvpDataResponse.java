package io.pinkspider.leveluptogethermvp.bffservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.facade.dto.SeasonDto;
import io.pinkspider.global.facade.dto.SeasonMvpGuildDto;
import io.pinkspider.global.facade.dto.SeasonMvpPlayerDto;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.MvpGuildResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.TodayPlayerResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 홈 화면 MVP 섹션 BFF 응답 DTO
 * 홈 피드와 분리하여 MVP 관련 데이터만 반환합니다. (QA-222)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class HomeMvpDataResponse {

    /**
     * MVP 유저 랭킹 (금일 EXP 획득 기준 상위 5명)
     */
    private List<TodayPlayerResponse> rankings;

    /**
     * MVP 길드 랭킹 (금일 EXP 획득 기준 상위 5개)
     */
    private List<MvpGuildResponse> mvpGuilds;

    /**
     * 현재 시즌 정보 (null이면 활성 시즌 없음)
     */
    private SeasonDto currentSeason;

    /**
     * 시즌 MVP 유저 랭킹 (시즌 기간 EXP 획득 기준)
     */
    private List<SeasonMvpPlayerDto> seasonMvpPlayers;

    /**
     * 시즌 MVP 길드 랭킹 (시즌 기간 EXP 획득 기준)
     */
    private List<SeasonMvpGuildDto> seasonMvpGuilds;
}
