package io.pinkspider.leveluptogethermvp.gamificationservice.season.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.CreateSeasonRankRewardRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonRankRewardResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.UpdateSeasonRankRewardRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRankReward;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRankRewardRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeasonRankRewardService 테스트")
class SeasonRankRewardServiceTest {

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private SeasonRankRewardRepository rankRewardRepository;

    @Mock
    private TitleRepository titleRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository
        shopItemRepository;

    @InjectMocks
    private SeasonRankRewardService seasonRankRewardService;

    private Season testSeason;
    private Title testTitle;
    private SeasonRankReward testReward;

    @BeforeEach
    void setUp() throws Exception {
        testSeason = Season.builder()
            .title("테스트 시즌")
            .description("테스트 시즌 설명")
            .startAt(LocalDateTime.now().minusDays(30))
            .endAt(LocalDateTime.now().minusDays(1))
            .isActive(true)
            .build();
        setId(testSeason, 1L);

        testTitle = Title.builder()
            .name("시즌1 챔피언")
            .rarity(TitleRarity.LEGENDARY)
            .positionType(TitlePosition.RIGHT)
            .acquisitionType(TitleAcquisitionType.SEASON)
            .isActive(true)
            .build();
        setId(testTitle, 100L);

        testReward = SeasonRankReward.builder()
            .season(testSeason)
            .rankStart(1)
            .rankEnd(1)
            .titleId(100L)
            .titleName("시즌1 챔피언")
            .sortOrder(1)
            .isActive(true)
            .build();
        setId(testReward, 1L);
    }

    @Nested
    @DisplayName("getSeasonRankRewards")
    class GetSeasonRankRewardsTest {

        @Test
        @DisplayName("시즌의 순위별 보상 목록을 조회한다")
        void success() {
            // given
            when(rankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testReward));

            // when
            List<SeasonRankRewardResponse> result = seasonRankRewardService.getSeasonRankRewards(1L);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).rankStart()).isEqualTo(1);
            assertThat(result.get(0).rankEnd()).isEqualTo(1);
            assertThat(result.get(0).titleName()).isEqualTo("시즌1 챔피언");
        }
    }

    @Nested
    @DisplayName("createRankReward")
    class CreateRankRewardTest {

        @Test
        @DisplayName("순위별 보상을 생성한다")
        void success() {
            // given
            CreateSeasonRankRewardRequest request = new CreateSeasonRankRewardRequest(1, 1, 100L, null, 1);
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithNullCategory(anyLong(), anyInt(), anyInt(), anyLong())).thenReturn(false);
            when(titleRepository.findById(100L)).thenReturn(Optional.of(testTitle));
            when(rankRewardRepository.save(any(SeasonRankReward.class))).thenReturn(testReward);

            // when
            SeasonRankRewardResponse result = seasonRankRewardService.createRankReward(1L, request);

            // then
            assertThat(result.rankStart()).isEqualTo(1);
            assertThat(result.rankEnd()).isEqualTo(1);
            assertThat(result.titleName()).isEqualTo("시즌1 챔피언");
            verify(rankRewardRepository).save(any(SeasonRankReward.class));
        }

        @Test
        @DisplayName("시즌이 없으면 예외를 던진다")
        void failWhenSeasonNotFound() {
            // given
            CreateSeasonRankRewardRequest request = new CreateSeasonRankRewardRequest(1, 1, 100L, null, 1);
            when(seasonRepository.findById(1L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonRankRewardService.createRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.season.not_found");
        }

        @Test
        @DisplayName("시작 순위가 종료 순위보다 크면 예외를 던진다")
        void failWhenInvalidRankRange() {
            // given
            CreateSeasonRankRewardRequest request = new CreateSeasonRankRewardRequest(10, 1, 100L, null, 1);
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));

            // when & then
            assertThatThrownBy(() -> seasonRankRewardService.createRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.season.rank.invalid_range");
        }

        @Test
        @DisplayName("순위 구간이 중복되면 예외를 던진다")
        void failWhenRankRangeOverlap() {
            // given
            CreateSeasonRankRewardRequest request = new CreateSeasonRankRewardRequest(1, 5, 100L, null, 1);
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithNullCategory(1L, 1, 5, 0L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> seasonRankRewardService.createRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.season.rank.overlap");
        }
    }

    @Nested
    @DisplayName("updateRankReward")
    class UpdateRankRewardTest {

        @Test
        @DisplayName("순위별 보상을 수정한다")
        void success() {
            // given
            UpdateSeasonRankRewardRequest request = new UpdateSeasonRankRewardRequest(1, 3, 100L, null, 1);
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));
            when(rankRewardRepository.existsOverlappingRangeWithNullCategory(1L, 1, 3, 1L)).thenReturn(false);
            when(titleRepository.findById(100L)).thenReturn(Optional.of(testTitle));

            // when
            SeasonRankRewardResponse result = seasonRankRewardService.updateRankReward(1L, request);

            // then
            assertThat(result.rankStart()).isEqualTo(1);
            assertThat(result.rankEnd()).isEqualTo(3);
        }

        @Test
        @DisplayName("보상이 없으면 예외를 던진다")
        void failWhenRewardNotFound() {
            // given
            UpdateSeasonRankRewardRequest request = new UpdateSeasonRankRewardRequest(1, 3, 100L, null, 1);
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonRankRewardService.updateRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.season.reward.not_found");
        }
    }

    @Nested
    @DisplayName("deleteRankReward")
    class DeleteRankRewardTest {

        @Test
        @DisplayName("순위별 보상을 삭제(비활성화)한다")
        void success() {
            // given
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));

            // when
            seasonRankRewardService.deleteRankReward(1L);

            // then
            assertThat(testReward.getIsActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("분기 보강 — 수정 검증·등급 null·아이템 스냅샷·시즌 칭호 생성")
    class BranchCoverageTest {

        private Title titleWithoutRarity() {
            Title t = Title.builder()
                .name("등급없음")
                .positionType(TitlePosition.LEFT)
                .acquisitionType(TitleAcquisitionType.SEASON)
                .isActive(true)
                .build();
            setId(t, 200L);
            return t;
        }

        @Test
        @DisplayName("createRankReward — 칭호 등급이 없으면 titleRarity 를 null 로 저장한다")
        void create_titleWithoutRarity_storesNullRarity() {
            CreateSeasonRankRewardRequest request = new CreateSeasonRankRewardRequest(1, 1, 200L, null, 1);
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithNullCategory(anyLong(), anyInt(), anyInt(), anyLong()))
                .thenReturn(false);
            when(titleRepository.findById(200L)).thenReturn(Optional.of(titleWithoutRarity()));
            when(rankRewardRepository.save(any(SeasonRankReward.class))).thenAnswer(inv -> inv.getArgument(0));

            SeasonRankRewardResponse result = seasonRankRewardService.createRankReward(1L, request);

            assertThat(result.titleName()).isEqualTo("등급없음");
            assertThat(result.titleRarity()).isNull();
            assertThat(result.itemId()).isNull();
            assertThat(result.itemName()).isNull();
        }

        @Test
        @DisplayName("createRankReward — 보상 아이템 ID 가 있으면 상점 아이템 이름을 스냅샷한다 (LUT-339)")
        void create_withItem_snapshotsItemName() {
            CreateSeasonRankRewardRequest request = new CreateSeasonRankRewardRequest(1, 1, 100L, 77L, 1);
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithNullCategory(anyLong(), anyInt(), anyInt(), anyLong()))
                .thenReturn(false);
            when(titleRepository.findById(100L)).thenReturn(Optional.of(testTitle));
            io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem item =
                io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem.builder()
                    .name("황금 날개").build();
            when(shopItemRepository.findById(77L)).thenReturn(Optional.of(item));
            when(rankRewardRepository.save(any(SeasonRankReward.class))).thenAnswer(inv -> inv.getArgument(0));

            SeasonRankRewardResponse result = seasonRankRewardService.createRankReward(1L, request);

            assertThat(result.itemId()).isEqualTo(77L);
            assertThat(result.itemName()).isEqualTo("황금 날개");
            assertThat(result.titleRarity()).isEqualTo("LEGENDARY");
        }

        @Test
        @DisplayName("createRankReward — 보상 아이템이 없으면 120602 예외를 던진다")
        void create_itemNotFound_throws() {
            CreateSeasonRankRewardRequest request = new CreateSeasonRankRewardRequest(1, 1, 100L, 404L, 1);
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithNullCategory(anyLong(), anyInt(), anyInt(), anyLong()))
                .thenReturn(false);
            when(titleRepository.findById(100L)).thenReturn(Optional.of(testTitle));
            when(shopItemRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seasonRankRewardService.createRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.useritem.item_not_found");
        }

        @Test
        @DisplayName("updateRankReward — 시작 순위가 종료 순위보다 크면 예외를 던진다")
        void update_invalidRange_throws() {
            UpdateSeasonRankRewardRequest request = new UpdateSeasonRankRewardRequest(5, 3, 100L, null, 1);
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));

            assertThatThrownBy(() -> seasonRankRewardService.updateRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.season.rank.invalid_range");
        }

        @Test
        @DisplayName("updateRankReward — 다른 보상과 순위 구간이 겹치면 예외를 던진다")
        void update_overlap_throws() {
            UpdateSeasonRankRewardRequest request = new UpdateSeasonRankRewardRequest(1, 3, 100L, null, 1);
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));
            when(rankRewardRepository.existsOverlappingRangeWithNullCategory(1L, 1, 3, 1L)).thenReturn(true);

            assertThatThrownBy(() -> seasonRankRewardService.updateRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.season.rank.overlap");
        }

        @Test
        @DisplayName("updateRankReward — 등급 없는 칭호로 바꾸고 sortOrder 가 null 이면 정렬순서는 유지된다")
        void update_titleWithoutRarity_nullSortOrder_keepsSortOrder() {
            UpdateSeasonRankRewardRequest request = new UpdateSeasonRankRewardRequest(1, 3, 200L, null, null);
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));
            when(rankRewardRepository.existsOverlappingRangeWithNullCategory(1L, 1, 3, 1L)).thenReturn(false);
            when(titleRepository.findById(200L)).thenReturn(Optional.of(titleWithoutRarity()));

            SeasonRankRewardResponse result = seasonRankRewardService.updateRankReward(1L, request);

            assertThat(result.titleId()).isEqualTo(200L);
            assertThat(result.titleRarity()).isNull();
            assertThat(result.sortOrder()).isEqualTo(1); // 기존 값 유지
            assertThat(result.itemName()).isNull();
        }

        @Test
        @DisplayName("updateRankReward — 보상 아이템을 지정하면 이름을 스냅샷하고 sortOrder 를 갱신한다")
        void update_withItem_andSortOrder() {
            UpdateSeasonRankRewardRequest request = new UpdateSeasonRankRewardRequest(1, 3, 100L, 77L, 9);
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));
            when(rankRewardRepository.existsOverlappingRangeWithNullCategory(1L, 1, 3, 1L)).thenReturn(false);
            when(titleRepository.findById(100L)).thenReturn(Optional.of(testTitle));
            io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem item =
                io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem.builder()
                    .name("은빛 날개").build();
            when(shopItemRepository.findById(77L)).thenReturn(Optional.of(item));

            SeasonRankRewardResponse result = seasonRankRewardService.updateRankReward(1L, request);

            assertThat(result.itemId()).isEqualTo(77L);
            assertThat(result.itemName()).isEqualTo("은빛 날개");
            assertThat(result.sortOrder()).isEqualTo(9);
        }

        @Test
        @DisplayName("createSeasonTitle — 시즌명과 순위 구간이 모두 있으면 획득 조건에 조합해 넣는다")
        void createSeasonTitle_withSeasonNameAndRankRange() {
            var request = new io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto
                .CreateSeasonTitleRequest("시즌1 챔피언", "Champion", null, null, "설명",
                TitleRarity.LEGENDARY, TitlePosition.RIGHT, null, "2026 봄", "1위");
            when(titleRepository.save(any(Title.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = seasonRankRewardService.createSeasonTitle(request);

            assertThat(result.getName()).isEqualTo("시즌1 챔피언");
            assertThat(result.getAcquisitionCondition()).isEqualTo("2026 봄 시즌 1위 달성");
        }

        @Test
        @DisplayName("createSeasonTitle — 시즌명이 없으면 기본 획득 조건을 쓴다")
        void createSeasonTitle_withoutSeasonName_usesDefaultCondition() {
            var request = new io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto
                .CreateSeasonTitleRequest("시즌 칭호", null, null, null, null,
                TitleRarity.RARE, TitlePosition.LEFT, null, null, "1~3위");
            when(titleRepository.save(any(Title.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = seasonRankRewardService.createSeasonTitle(request);

            assertThat(result.getAcquisitionCondition()).isEqualTo("시즌 랭킹 보상");
        }

        @Test
        @DisplayName("createSeasonTitle — 순위 구간이 없으면 기본 획득 조건을 쓴다")
        void createSeasonTitle_withoutRankRange_usesDefaultCondition() {
            var request = new io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto
                .CreateSeasonTitleRequest("시즌 칭호", null, null, null, null,
                TitleRarity.EPIC, TitlePosition.LEFT, null, "2026 봄", null);
            when(titleRepository.save(any(Title.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = seasonRankRewardService.createSeasonTitle(request);

            assertThat(result.getAcquisitionCondition()).isEqualTo("시즌 랭킹 보상");
        }
    }
}
