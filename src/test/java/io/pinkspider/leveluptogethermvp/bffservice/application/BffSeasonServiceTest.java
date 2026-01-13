package io.pinkspider.leveluptogethermvp.bffservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.SeasonMvpGuildResponse;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.SeasonMvpPlayerResponse;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.SeasonResponse;
import io.pinkspider.leveluptogethermvp.adminservice.application.SeasonRankingService;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.SeasonRankReward;
import io.pinkspider.leveluptogethermvp.adminservice.domain.enums.SeasonStatus;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.SeasonRankRewardRepository;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.SeasonDetailResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.SeasonMyRankingResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BffSeasonServiceTest {

    @Mock
    private SeasonRankingService seasonRankingService;

    @Mock
    private SeasonRankRewardRepository seasonRankRewardRepository;

    @Mock
    private MissionCategoryService missionCategoryService;

    @InjectMocks
    private BffSeasonService bffSeasonService;

    private String testUserId;
    private Season testSeason;
    private SeasonRankReward testRankReward;
    private SeasonMvpPlayerResponse testPlayerRanking;
    private SeasonMvpGuildResponse testGuildRanking;
    private SeasonMyRankingResponse testMyRanking;
    private MissionCategoryResponse testCategory;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";

        testSeason = Season.builder()
            .title("2025 윈터 시즌")
            .description("겨울 시즌 이벤트입니다.")
            .startAt(LocalDateTime.of(2025, 1, 1, 0, 0))
            .endAt(LocalDateTime.of(2025, 3, 31, 23, 59))
            .rewardTitleId(100L)
            .rewardTitleName("윈터 챔피언")
            .build();
        setSeasonId(testSeason, 1L);

        testRankReward = SeasonRankReward.builder()
            .season(testSeason)
            .rankStart(1)
            .rankEnd(1)
            .titleId(101L)
            .titleName("골드 챔피언")
            .sortOrder(1)
            .isActive(true)
            .build();
        setRankRewardId(testRankReward, 1L);

        testPlayerRanking = SeasonMvpPlayerResponse.of(
            "user-1",
            "플레이어1",
            "https://example.com/profile.jpg",
            15,
            "모험가",
            TitleRarity.RARE,
            10000L,
            1
        );

        testGuildRanking = SeasonMvpGuildResponse.of(
            1L,
            "테스트 길드",
            "https://example.com/guild.jpg",
            10,
            25,
            50000L,
            1
        );

        testMyRanking = SeasonMyRankingResponse.of(
            5,
            8500L,
            3,
            45000L,
            1L,
            "테스트 길드"
        );

        testCategory = MissionCategoryResponse.builder()
            .id(1L)
            .name("운동")
            .nameEn("Exercise")
            .icon("🏃")
            .isActive(true)
            .build();
    }

    private void setSeasonId(Season season, Long id) {
        try {
            Field idField = Season.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(season, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setRankRewardId(SeasonRankReward reward, Long id) {
        try {
            Field idField = SeasonRankReward.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(reward, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("시즌 상세 데이터 조회 테스트")
    class GetSeasonDetailTest {

        @Test
        @DisplayName("시즌 상세 데이터를 조회한다")
        void getSeasonDetail_success() {
            // given
            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testRankReward));
            when(seasonRankingService.getSeasonPlayerRankings(any(Season.class), any(), anyInt(), any()))
                .thenReturn(List.of(testPlayerRanking));
            when(seasonRankingService.getSeasonGuildRankings(any(Season.class), anyInt()))
                .thenReturn(List.of(testGuildRanking));
            when(seasonRankingService.getMySeasonRanking(any(Season.class), anyString()))
                .thenReturn(testMyRanking);
            when(missionCategoryService.getActiveCategories())
                .thenReturn(List.of(testCategory));

            // when
            SeasonDetailResponse response = bffSeasonService.getSeasonDetail(1L, testUserId, null, "ko");

            // then
            assertThat(response).isNotNull();
            assertThat(response.season()).isNotNull();
            assertThat(response.season().id()).isEqualTo(1L);
            assertThat(response.season().title()).isEqualTo("2025 윈터 시즌");
            assertThat(response.rankRewards()).hasSize(1);
            assertThat(response.playerRankings()).hasSize(1);
            assertThat(response.guildRankings()).hasSize(1);
            assertThat(response.myRanking()).isNotNull();
            assertThat(response.myRanking().playerRank()).isEqualTo(5);
            assertThat(response.categories()).hasSize(1);
        }

        @Test
        @DisplayName("카테고리명을 지정하여 시즌 상세 데이터를 조회한다")
        void getSeasonDetail_withCategory_success() {
            // given
            String categoryName = "운동";
            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testRankReward));
            when(seasonRankingService.getSeasonPlayerRankings(any(Season.class), any(), anyInt(), any()))
                .thenReturn(List.of(testPlayerRanking));
            when(seasonRankingService.getSeasonGuildRankings(any(Season.class), anyInt()))
                .thenReturn(List.of(testGuildRanking));
            when(seasonRankingService.getMySeasonRanking(any(Season.class), anyString()))
                .thenReturn(testMyRanking);
            when(missionCategoryService.getActiveCategories())
                .thenReturn(List.of(testCategory));

            // when
            SeasonDetailResponse response = bffSeasonService.getSeasonDetail(1L, testUserId, categoryName, "ko");

            // then
            assertThat(response).isNotNull();
            assertThat(response.season()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 시즌 조회 시 예외를 던진다")
        void getSeasonDetail_seasonNotFound_throwsException() {
            // given
            when(seasonRankingService.getSeasonById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> bffSeasonService.getSeasonDetail(999L, testUserId, null, "ko"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시즌을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("보상 조회 실패 시 빈 목록을 반환한다")
        void getSeasonDetail_rewardsFetchFailed_returnsEmptyList() {
            // given
            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenThrow(new RuntimeException("DB 오류"));
            when(seasonRankingService.getSeasonPlayerRankings(any(Season.class), any(), anyInt(), any()))
                .thenReturn(List.of(testPlayerRanking));
            when(seasonRankingService.getSeasonGuildRankings(any(Season.class), anyInt()))
                .thenReturn(List.of(testGuildRanking));
            when(seasonRankingService.getMySeasonRanking(any(Season.class), anyString()))
                .thenReturn(testMyRanking);
            when(missionCategoryService.getActiveCategories())
                .thenReturn(List.of(testCategory));

            // when
            SeasonDetailResponse response = bffSeasonService.getSeasonDetail(1L, testUserId, null, "ko");

            // then
            assertThat(response).isNotNull();
            assertThat(response.rankRewards()).isEmpty();
        }

        @Test
        @DisplayName("플레이어 랭킹 조회 실패 시 빈 목록을 반환한다")
        void getSeasonDetail_playerRankingsFetchFailed_returnsEmptyList() {
            // given
            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testRankReward));
            when(seasonRankingService.getSeasonPlayerRankings(any(Season.class), any(), anyInt(), any()))
                .thenThrow(new RuntimeException("DB 오류"));
            when(seasonRankingService.getSeasonGuildRankings(any(Season.class), anyInt()))
                .thenReturn(List.of(testGuildRanking));
            when(seasonRankingService.getMySeasonRanking(any(Season.class), anyString()))
                .thenReturn(testMyRanking);
            when(missionCategoryService.getActiveCategories())
                .thenReturn(List.of(testCategory));

            // when
            SeasonDetailResponse response = bffSeasonService.getSeasonDetail(1L, testUserId, null, "ko");

            // then
            assertThat(response).isNotNull();
            assertThat(response.playerRankings()).isEmpty();
        }

        @Test
        @DisplayName("길드 랭킹 조회 실패 시 빈 목록을 반환한다")
        void getSeasonDetail_guildRankingsFetchFailed_returnsEmptyList() {
            // given
            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testRankReward));
            when(seasonRankingService.getSeasonPlayerRankings(any(Season.class), any(), anyInt(), any()))
                .thenReturn(List.of(testPlayerRanking));
            when(seasonRankingService.getSeasonGuildRankings(any(Season.class), anyInt()))
                .thenThrow(new RuntimeException("DB 오류"));
            when(seasonRankingService.getMySeasonRanking(any(Season.class), anyString()))
                .thenReturn(testMyRanking);
            when(missionCategoryService.getActiveCategories())
                .thenReturn(List.of(testCategory));

            // when
            SeasonDetailResponse response = bffSeasonService.getSeasonDetail(1L, testUserId, null, "ko");

            // then
            assertThat(response).isNotNull();
            assertThat(response.guildRankings()).isEmpty();
        }

        @Test
        @DisplayName("내 랭킹 조회 실패 시 빈 랭킹 정보를 반환한다")
        void getSeasonDetail_myRankingFetchFailed_returnsEmptyRanking() {
            // given
            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testRankReward));
            when(seasonRankingService.getSeasonPlayerRankings(any(Season.class), any(), anyInt(), any()))
                .thenReturn(List.of(testPlayerRanking));
            when(seasonRankingService.getSeasonGuildRankings(any(Season.class), anyInt()))
                .thenReturn(List.of(testGuildRanking));
            when(seasonRankingService.getMySeasonRanking(any(Season.class), anyString()))
                .thenThrow(new RuntimeException("DB 오류"));
            when(missionCategoryService.getActiveCategories())
                .thenReturn(List.of(testCategory));

            // when
            SeasonDetailResponse response = bffSeasonService.getSeasonDetail(1L, testUserId, null, "ko");

            // then
            assertThat(response).isNotNull();
            assertThat(response.myRanking()).isNotNull();
            assertThat(response.myRanking().playerRank()).isNull();
        }

        @Test
        @DisplayName("카테고리 조회 실패 시 빈 목록을 반환한다")
        void getSeasonDetail_categoriesFetchFailed_returnsEmptyList() {
            // given
            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testRankReward));
            when(seasonRankingService.getSeasonPlayerRankings(any(Season.class), any(), anyInt(), any()))
                .thenReturn(List.of(testPlayerRanking));
            when(seasonRankingService.getSeasonGuildRankings(any(Season.class), anyInt()))
                .thenReturn(List.of(testGuildRanking));
            when(seasonRankingService.getMySeasonRanking(any(Season.class), anyString()))
                .thenReturn(testMyRanking);
            when(missionCategoryService.getActiveCategories())
                .thenThrow(new RuntimeException("DB 오류"));

            // when
            SeasonDetailResponse response = bffSeasonService.getSeasonDetail(1L, testUserId, null, "ko");

            // then
            assertThat(response).isNotNull();
            assertThat(response.categories()).isEmpty();
        }
    }

    @Nested
    @DisplayName("현재 활성 시즌 상세 데이터 조회 테스트")
    class GetCurrentSeasonDetailTest {

        @Test
        @DisplayName("현재 활성 시즌 상세 데이터를 조회한다")
        void getCurrentSeasonDetail_success() {
            // given
            SeasonResponse currentSeasonResponse = new SeasonResponse(
                1L, "2025 윈터 시즌", "겨울 시즌 이벤트",
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 3, 31, 23, 59),
                100L, "윈터 챔피언",
                SeasonStatus.ACTIVE, "진행중"
            );

            when(seasonRankingService.getCurrentSeason()).thenReturn(Optional.of(currentSeasonResponse));
            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testRankReward));
            when(seasonRankingService.getSeasonPlayerRankings(any(Season.class), any(), anyInt(), any()))
                .thenReturn(List.of(testPlayerRanking));
            when(seasonRankingService.getSeasonGuildRankings(any(Season.class), anyInt()))
                .thenReturn(List.of(testGuildRanking));
            when(seasonRankingService.getMySeasonRanking(any(Season.class), anyString()))
                .thenReturn(testMyRanking);
            when(missionCategoryService.getActiveCategories())
                .thenReturn(List.of(testCategory));

            // when
            SeasonDetailResponse response = bffSeasonService.getCurrentSeasonDetail(testUserId, null, "ko");

            // then
            assertThat(response).isNotNull();
            assertThat(response.season()).isNotNull();
            assertThat(response.season().id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("활성 시즌이 없는 경우 예외를 던진다")
        void getCurrentSeasonDetail_noActiveSeason_throwsException() {
            // given
            when(seasonRankingService.getCurrentSeason()).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> bffSeasonService.getCurrentSeasonDetail(testUserId, null, "ko"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("현재 활성화된 시즌이 없습니다");
        }

        @Test
        @DisplayName("현재 시즌 응답은 있지만 시즌 엔티티 조회 실패 시 예외를 던진다")
        void getCurrentSeasonDetail_seasonEntityNotFound_throwsException() {
            // given
            SeasonResponse currentSeasonResponse = new SeasonResponse(
                1L, "2025 윈터 시즌", "겨울 시즌 이벤트",
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 3, 31, 23, 59),
                100L, "윈터 챔피언",
                SeasonStatus.ACTIVE, "진행중"
            );

            when(seasonRankingService.getCurrentSeason()).thenReturn(Optional.of(currentSeasonResponse));
            when(seasonRankingService.getSeasonById(1L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> bffSeasonService.getCurrentSeasonDetail(testUserId, null, "ko"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("현재 활성화된 시즌이 없습니다");
        }
    }

    @Nested
    @DisplayName("SeasonMyRankingResponse 테스트")
    class SeasonMyRankingResponseTest {

        @Test
        @DisplayName("빈 랭킹 정보를 생성한다")
        void empty_createsEmptyRanking() {
            // when
            SeasonMyRankingResponse emptyRanking = SeasonMyRankingResponse.empty();

            // then
            assertThat(emptyRanking).isNotNull();
            assertThat(emptyRanking.playerRank()).isNull();
            assertThat(emptyRanking.playerSeasonExp()).isEqualTo(0L);
            assertThat(emptyRanking.guildRank()).isNull();
            assertThat(emptyRanking.guildSeasonExp()).isNull();
            assertThat(emptyRanking.guildId()).isNull();
            assertThat(emptyRanking.guildName()).isNull();
        }

        @Test
        @DisplayName("랭킹 정보를 생성한다")
        void of_createsRanking() {
            // when
            SeasonMyRankingResponse ranking = SeasonMyRankingResponse.of(
                1, 10000L, 2, 50000L, 100L, "최강 길드"
            );

            // then
            assertThat(ranking.playerRank()).isEqualTo(1);
            assertThat(ranking.playerSeasonExp()).isEqualTo(10000L);
            assertThat(ranking.guildRank()).isEqualTo(2);
            assertThat(ranking.guildSeasonExp()).isEqualTo(50000L);
            assertThat(ranking.guildId()).isEqualTo(100L);
            assertThat(ranking.guildName()).isEqualTo("최강 길드");
        }
    }

    @Nested
    @DisplayName("SeasonDetailResponse 테스트")
    class SeasonDetailResponseTest {

        @Test
        @DisplayName("시즌 상세 응답을 생성한다")
        void of_createsResponse() {
            // given
            SeasonResponse seasonResponse = new SeasonResponse(
                1L, "테스트 시즌", "설명",
                LocalDateTime.now(), LocalDateTime.now().plusMonths(3),
                100L, "보상 칭호",
                SeasonStatus.ACTIVE, "진행중"
            );

            // when
            SeasonDetailResponse response = SeasonDetailResponse.of(
                seasonResponse,
                Collections.emptyList(),
                List.of(testPlayerRanking),
                List.of(testGuildRanking),
                testMyRanking,
                List.of(testCategory)
            );

            // then
            assertThat(response).isNotNull();
            assertThat(response.season()).isEqualTo(seasonResponse);
            assertThat(response.rankRewards()).isEmpty();
            assertThat(response.playerRankings()).hasSize(1);
            assertThat(response.guildRankings()).hasSize(1);
            assertThat(response.myRanking()).isEqualTo(testMyRanking);
            assertThat(response.categories()).hasSize(1);
        }
    }
}
