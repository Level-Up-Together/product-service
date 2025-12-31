package io.pinkspider.leveluptogethermvp.bffservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildDetailDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildListDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.HomeDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.MissionTodayDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.application.BffGuildService;
import io.pinkspider.leveluptogethermvp.bffservice.application.BffHomeService;
import io.pinkspider.leveluptogethermvp.bffservice.application.BffMissionService;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * BFF (Backend for Frontend) 컨트롤러
 * 여러 화면에 필요한 데이터를 한 번의 API 호출로 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/bff")
@RequiredArgsConstructor
public class BffHomeController {

    private final BffHomeService bffHomeService;
    private final BffGuildService bffGuildService;
    private final BffMissionService bffMissionService;

    /**
     * 홈 화면 데이터 조회 (BFF)
     * <p>
     * 다음 데이터를 한 번에 조회합니다:
     * - 피드 목록 (페이징, 카테고리 필터)
     * - 오늘의 플레이어 랭킹 (카테고리 필터)
     * - 미션 카테고리 목록
     * - 내 길드 목록
     * - 공개 길드 목록 (카테고리 필터)
     * - 활성 공지사항 목록
     *
     * @param userId 인증된 사용자 ID
     * @param categoryId 카테고리 ID (선택적, null이면 전체)
     * @param feedPage 피드 페이지 번호 (기본: 0)
     * @param feedSize 피드 페이지 크기 (기본: 20)
     * @param publicGuildSize 공개 길드 조회 개수 (기본: 5)
     * @return HomeDataResponse
     */
    @GetMapping("/home")
    public ResponseEntity<ApiResult<HomeDataResponse>> getHomeData(
        @CurrentUser String userId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(defaultValue = "0") int feedPage,
        @RequestParam(defaultValue = "20") int feedSize,
        @RequestParam(defaultValue = "5") int publicGuildSize
    ) {
        HomeDataResponse response = bffHomeService.getHomeData(userId, categoryId, feedPage, feedSize, publicGuildSize);
        return ResponseEntity.ok(ApiResult.<HomeDataResponse>builder().value(response).build());
    }

    /**
     * 길드 상세 화면 데이터 조회 (BFF)
     * <p>
     * 다음 데이터를 한 번에 조회합니다:
     * - 길드 상세 정보
     * - 길드 멤버 목록
     * - 길드 게시글 목록 (페이징)
     * - 멤버 여부 및 역할
     *
     * @param userId 인증된 사용자 ID
     * @param guildId 길드 ID
     * @param postPage 게시글 페이지 번호 (기본: 0)
     * @param postSize 게시글 페이지 크기 (기본: 20)
     * @return GuildDetailDataResponse
     */
    @GetMapping("/guild/{guildId}")
    public ResponseEntity<ApiResult<GuildDetailDataResponse>> getGuildDetail(
        @CurrentUser String userId,
        @PathVariable Long guildId,
        @RequestParam(defaultValue = "0") int postPage,
        @RequestParam(defaultValue = "20") int postSize
    ) {
        GuildDetailDataResponse response = bffGuildService.getGuildDetail(guildId, userId, postPage, postSize);
        return ResponseEntity.ok(ApiResult.<GuildDetailDataResponse>builder().value(response).build());
    }

    /**
     * 길드 목록 화면 데이터 조회 (BFF)
     * <p>
     * 다음 데이터를 한 번에 조회합니다:
     * - 내 길드 목록
     * - 추천 공개 길드 목록
     * - 내 첫 번째 길드의 공지사항 (길드 가입 시)
     * - 내 첫 번째 길드의 활동 피드 (길드 가입 시)
     *
     * @param userId 인증된 사용자 ID
     * @param recommendedGuildSize 추천 길드 조회 개수 (기본: 10)
     * @param activityFeedSize 활동 피드 조회 개수 (기본: 10)
     * @return GuildListDataResponse
     */
    @GetMapping("/guild/list")
    public ResponseEntity<ApiResult<GuildListDataResponse>> getGuildList(
        @CurrentUser String userId,
        @RequestParam(defaultValue = "10") int recommendedGuildSize,
        @RequestParam(defaultValue = "10") int activityFeedSize
    ) {
        GuildListDataResponse response = bffGuildService.getGuildList(userId, recommendedGuildSize, activityFeedSize);
        return ResponseEntity.ok(ApiResult.<GuildListDataResponse>builder().value(response).build());
    }

    /**
     * 오늘의 미션 화면 데이터 조회 (BFF)
     * <p>
     * 다음 데이터를 한 번에 조회합니다:
     * - 내 미션 목록
     * - 오늘의 미션 실행 현황
     * - 완료/진행중/미완료 통계
     *
     * @param userId 인증된 사용자 ID
     * @return MissionTodayDataResponse
     */
    @GetMapping("/mission/today")
    public ResponseEntity<ApiResult<MissionTodayDataResponse>> getTodayMissions(
        @CurrentUser String userId
    ) {
        MissionTodayDataResponse response = bffMissionService.getTodayMissions(userId);
        return ResponseEntity.ok(ApiResult.<MissionTodayDataResponse>builder().value(response).build());
    }
}
