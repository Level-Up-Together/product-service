package io.pinkspider.leveluptogethermvp.gamificationservice.season.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.CreateSeasonRankRewardAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRankRewardAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRewardHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRewardStatsAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.UpdateSeasonRankRewardAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRankReward;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRewardHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.enums.SeasonRewardStatus;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRankRewardRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRewardHistoryRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeasonRankRewardAdminService 테스트")
class SeasonRankRewardAdminServiceTest {

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private SeasonRankRewardRepository rankRewardRepository;

    @Mock
    private SeasonRewardHistoryRepository rewardHistoryRepository;

    @Mock
    private TitleRepository titleRepository;

    @InjectMocks
    private SeasonRankRewardAdminService seasonRankRewardAdminService;

    private Season testSeason;
    private Title testTitle;
    private SeasonRankReward testReward;

    @BeforeEach
    void setUp() throws Exception {
        testSeason = Season.builder()
            .title("테스트 시즌")
            .description("설명")
            .startAt(LocalDateTime.now().minusDays(30))
            .endAt(LocalDateTime.now().plusDays(30))
            .isActive(true)
            .build();
        setId(testSeason, 1L);

        testTitle = Title.builder()
            .name("챔피언")
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
            .titleName("챔피언")
            .titleRarity("LEGENDARY")
            .sortOrder(1)
            .isActive(true)
            .build();
        setId(testReward, 1L);
    }

    @Nested
    @DisplayName("getSeasonRankRewards 테스트")
    class GetSeasonRankRewardsTest {

        @Test
        @DisplayName("시즌의 순위별 보상 목록을 조회한다")
        void getSeasonRankRewards_success() {
            // given
            when(rankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testReward));
            when(titleRepository.findAllById(List.of(100L)))
                .thenReturn(List.of(testTitle));

            // when
            List<SeasonRankRewardAdminResponse> result = seasonRankRewardAdminService.getSeasonRankRewards(1L);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).rankStart()).isEqualTo(1);
            assertThat(result.get(0).rankEnd()).isEqualTo(1);
            assertThat(result.get(0).titleName()).isEqualTo("챔피언");
            assertThat(result.get(0).titleRarity()).isEqualTo("LEGENDARY");
        }

        @Test
        @DisplayName("보상이 없으면 빈 목록을 반환한다")
        void getSeasonRankRewards_empty() {
            // given
            when(rankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of());
            when(titleRepository.findAllById(List.of()))
                .thenReturn(List.of());

            // when
            List<SeasonRankRewardAdminResponse> result = seasonRankRewardAdminService.getSeasonRankRewards(1L);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("칭호 정보가 없는 보상도 조회한다")
        void getSeasonRankRewards_withMissingTitle() {
            // given
            when(rankRewardRepository.findBySeasonIdOrderBySortOrder(1L))
                .thenReturn(List.of(testReward));
            when(titleRepository.findAllById(List.of(100L)))
                .thenReturn(List.of());  // 칭호가 DB에 없는 경우

            // when
            List<SeasonRankRewardAdminResponse> result = seasonRankRewardAdminService.getSeasonRankRewards(1L);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).titleRarity()).isNull();  // 칭호 없으면 null
        }
    }

    @Nested
    @DisplayName("createRankReward 테스트 (기존 칭호 사용)")
    class CreateRankRewardWithExistingTitleTest {

        @Test
        @DisplayName("기존 칭호로 순위 보상을 생성한다")
        void createRankReward_withExistingTitle_success() {
            // given
            CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
                1, 1, null, null, 100L, "챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 1, 1, 0L)).thenReturn(false);
            when(titleRepository.findById(100L)).thenReturn(Optional.of(testTitle));
            when(rankRewardRepository.save(any(SeasonRankReward.class))).thenReturn(testReward);

            // when
            SeasonRankRewardAdminResponse result = seasonRankRewardAdminService.createRankReward(1L, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.rankStart()).isEqualTo(1);
            assertThat(result.titleName()).isEqualTo("챔피언");
            verify(rankRewardRepository).save(any(SeasonRankReward.class));
        }

        @Test
        @DisplayName("기존 칭호 사용 시 칭호 속성을 업데이트한다")
        void createRankReward_withExistingTitle_updatesTitle() {
            // given
            CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
                1, 1, null, null, 100L, "새 챔피언", TitleRarity.MYTHIC, TitlePosition.LEFT, 1
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 1, 1, 0L)).thenReturn(false);
            when(titleRepository.findById(100L)).thenReturn(Optional.of(testTitle));
            when(rankRewardRepository.save(any(SeasonRankReward.class))).thenReturn(testReward);

            // when
            seasonRankRewardAdminService.createRankReward(1L, request);

            // then
            assertThat(testTitle.getName()).isEqualTo("새 챔피언");
            assertThat(testTitle.getRarity()).isEqualTo(TitleRarity.MYTHIC);
            assertThat(testTitle.getPositionType()).isEqualTo(TitlePosition.LEFT);
        }

        @Test
        @DisplayName("기존 칭호가 없으면 예외를 던진다")
        void createRankReward_titleNotFound() {
            // given
            CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
                1, 1, null, null, 999L, "챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 1, 1, 0L)).thenReturn(false);
            when(titleRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.createRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("칭호를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("createRankReward 테스트 (새 칭호 생성)")
    class CreateRankRewardWithNewTitleTest {

        @Test
        @DisplayName("새 칭호를 생성하며 순위 보상을 생성한다")
        void createRankReward_withNewTitle_success() {
            // given
            CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
                1, 3, null, null, null, "새 칭호", TitleRarity.EPIC, TitlePosition.RIGHT, 1
            );
            Title savedTitle = Title.builder()
                .name("새 칭호")
                .rarity(TitleRarity.EPIC)
                .positionType(TitlePosition.RIGHT)
                .acquisitionType(TitleAcquisitionType.SEASON)
                .isActive(true)
                .build();
            setId(savedTitle, 200L);

            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 1, 3, 0L)).thenReturn(false);
            when(titleRepository.save(any(Title.class))).thenReturn(savedTitle);
            when(rankRewardRepository.save(any(SeasonRankReward.class))).thenReturn(testReward);

            // when
            SeasonRankRewardAdminResponse result = seasonRankRewardAdminService.createRankReward(1L, request);

            // then
            assertThat(result).isNotNull();
            verify(titleRepository).save(any(Title.class));
            verify(rankRewardRepository).save(any(SeasonRankReward.class));
        }

        @Test
        @DisplayName("카테고리명이 있으면 획득 조건에 포함된다")
        void createRankReward_withCategory_buildCondition() {
            // given
            CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
                1, 1, 1L, "운동", null, "운동 챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            Title savedTitle = Title.builder()
                .name("운동 챔피언")
                .rarity(TitleRarity.LEGENDARY)
                .positionType(TitlePosition.RIGHT)
                .acquisitionType(TitleAcquisitionType.SEASON)
                .acquisitionCondition("테스트 시즌 운동 랭킹 1위")
                .isActive(true)
                .build();
            setId(savedTitle, 201L);

            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, 1L, 1, 1, 0L)).thenReturn(false);
            when(titleRepository.save(any(Title.class))).thenReturn(savedTitle);
            when(rankRewardRepository.save(any(SeasonRankReward.class))).thenReturn(testReward);

            // when
            seasonRankRewardAdminService.createRankReward(1L, request);

            // then
            verify(titleRepository).save(any(Title.class));
        }
    }

    @Nested
    @DisplayName("createRankReward 유효성 검사 테스트")
    class CreateRankRewardValidationTest {

        @Test
        @DisplayName("시즌이 없으면 예외를 던진다")
        void createRankReward_seasonNotFound() {
            // given
            CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
                1, 1, null, null, 100L, "챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(seasonRepository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.createRankReward(99L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시즌을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("시작 순위가 종료 순위보다 크면 예외를 던진다")
        void createRankReward_invalidRankRange() {
            // given
            CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
                10, 1, null, null, 100L, "챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.createRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시작 순위가 종료 순위보다 클 수 없습니다");
        }

        @Test
        @DisplayName("순위 구간이 겹치면 예외를 던진다")
        void createRankReward_overlappingRankRange() {
            // given
            CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
                1, 5, null, null, 100L, "챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 1, 5, 0L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.createRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("순위 구간이 기존 보상과 중복됩니다");
        }
    }

    @Nested
    @DisplayName("createBulkRankRewards 테스트")
    class CreateBulkRankRewardsTest {

        @Test
        @DisplayName("벌크로 순위 보상을 생성한다")
        void createBulkRankRewards_success() throws Exception {
            // given
            CreateSeasonRankRewardAdminRequest request1 = new CreateSeasonRankRewardAdminRequest(
                1, 1, null, null, null, "1위 칭호", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            CreateSeasonRankRewardAdminRequest request2 = new CreateSeasonRankRewardAdminRequest(
                2, 5, null, null, null, "2-5위 칭호", TitleRarity.EPIC, TitlePosition.RIGHT, 2
            );

            Title savedTitle1 = Title.builder()
                .name("1위 칭호").rarity(TitleRarity.LEGENDARY).positionType(TitlePosition.RIGHT)
                .acquisitionType(TitleAcquisitionType.SEASON).isActive(true).build();
            setId(savedTitle1, 201L);

            Title savedTitle2 = Title.builder()
                .name("2-5위 칭호").rarity(TitleRarity.EPIC).positionType(TitlePosition.RIGHT)
                .acquisitionType(TitleAcquisitionType.SEASON).isActive(true).build();
            setId(savedTitle2, 202L);

            SeasonRankReward savedReward1 = SeasonRankReward.builder()
                .season(testSeason).rankStart(1).rankEnd(1).titleId(201L)
                .titleName("1위 칭호").sortOrder(1).isActive(true).build();
            setId(savedReward1, 10L);

            SeasonRankReward savedReward2 = SeasonRankReward.builder()
                .season(testSeason).rankStart(2).rankEnd(5).titleId(202L)
                .titleName("2-5위 칭호").sortOrder(2).isActive(true).build();
            setId(savedReward2, 11L);

            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 1, 1, 0L)).thenReturn(false);
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 2, 5, 0L)).thenReturn(false);
            when(titleRepository.save(any(Title.class))).thenReturn(savedTitle1).thenReturn(savedTitle2);
            when(rankRewardRepository.save(any(SeasonRankReward.class))).thenReturn(savedReward1).thenReturn(savedReward2);

            // when
            List<SeasonRankRewardAdminResponse> result = seasonRankRewardAdminService.createBulkRankRewards(
                1L, List.of(request1, request2));

            // then
            assertThat(result).hasSize(2);
            verify(titleRepository, org.mockito.Mockito.times(2)).save(any(Title.class));
            verify(rankRewardRepository, org.mockito.Mockito.times(2)).save(any(SeasonRankReward.class));
        }

        @Test
        @DisplayName("시즌이 없으면 예외를 던진다")
        void createBulkRankRewards_seasonNotFound() {
            // given
            CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
                1, 1, null, null, null, "칭호", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(seasonRepository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.createBulkRankRewards(99L, List.of(request)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시즌을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("벌크 생성 시 시작 순위가 종료 순위보다 크면 예외를 던진다")
        void createBulkRankRewards_invalidRankRange() {
            // given
            CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
                10, 1, null, null, null, "칭호", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.createBulkRankRewards(1L, List.of(request)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시작 순위가 종료 순위보다 클 수 없습니다");
        }

        @Test
        @DisplayName("벌크 생성 시 순위 구간이 겹치면 예외를 던진다")
        void createBulkRankRewards_overlappingRankRange() {
            // given
            CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
                1, 5, null, null, null, "칭호", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 1, 5, 0L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.createBulkRankRewards(1L, List.of(request)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("순위 구간이 기존 보상과 중복됩니다");
        }

        @Test
        @DisplayName("카테고리명이 null이면 오류 메시지에 '전체'로 표시한다")
        void createBulkRankRewards_nullCategoryNameUsesDefault() {
            // given
            CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
                1, 5, 1L, null, null, "칭호", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, 1L, 1, 5, 0L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.createBulkRankRewards(1L, List.of(request)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("전체");
        }
    }

    @Nested
    @DisplayName("updateRankReward 테스트")
    class UpdateRankRewardTest {

        @Test
        @DisplayName("순위 보상을 수정한다")
        void updateRankReward_success() {
            // given
            UpdateSeasonRankRewardAdminRequest request = new UpdateSeasonRankRewardAdminRequest(
                1, 3, null, null, 100L, "업데이트 챔피언", TitleRarity.MYTHIC, TitlePosition.RIGHT, 2
            );
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 1, 3, 1L)).thenReturn(false);
            when(titleRepository.findById(100L)).thenReturn(Optional.of(testTitle));

            // when
            SeasonRankRewardAdminResponse result = seasonRankRewardAdminService.updateRankReward(1L, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.rankStart()).isEqualTo(1);
            assertThat(result.rankEnd()).isEqualTo(3);
        }

        @Test
        @DisplayName("보상이 없으면 예외를 던진다")
        void updateRankReward_notFound() {
            // given
            UpdateSeasonRankRewardAdminRequest request = new UpdateSeasonRankRewardAdminRequest(
                1, 3, null, null, 100L, "챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(rankRewardRepository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.updateRankReward(99L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("보상 설정을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("시작 순위가 종료 순위보다 크면 예외를 던진다")
        void updateRankReward_invalidRankRange() {
            // given
            UpdateSeasonRankRewardAdminRequest request = new UpdateSeasonRankRewardAdminRequest(
                10, 1, null, null, 100L, "챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.updateRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시작 순위가 종료 순위보다 클 수 없습니다");
        }

        @Test
        @DisplayName("순위 구간이 겹치면 예외를 던진다")
        void updateRankReward_overlappingRankRange() {
            // given
            UpdateSeasonRankRewardAdminRequest request = new UpdateSeasonRankRewardAdminRequest(
                1, 5, null, null, 100L, "챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 1, 5, 1L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.updateRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("순위 구간이 기존 보상과 중복됩니다");
        }

        @Test
        @DisplayName("칭호가 없으면 예외를 던진다")
        void updateRankReward_titleNotFound() {
            // given
            UpdateSeasonRankRewardAdminRequest request = new UpdateSeasonRankRewardAdminRequest(
                1, 3, null, null, 999L, "챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 1
            );
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 1, 3, 1L)).thenReturn(false);
            when(titleRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.updateRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("칭호를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("sortOrder가 null이면 변경하지 않는다")
        void updateRankReward_nullSortOrder() {
            // given
            UpdateSeasonRankRewardAdminRequest request = new UpdateSeasonRankRewardAdminRequest(
                1, 3, null, null, 100L, "챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, null
            );
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 1, 3, 1L)).thenReturn(false);
            when(titleRepository.findById(100L)).thenReturn(Optional.of(testTitle));

            // when
            SeasonRankRewardAdminResponse result = seasonRankRewardAdminService.updateRankReward(1L, request);

            // then
            assertThat(result).isNotNull();
            assertThat(testReward.getSortOrder()).isEqualTo(1);  // 기존 sortOrder 유지
        }

        @Test
        @DisplayName("칭호 희귀도가 null이면 titleRarity를 null로 설정한다")
        void updateRankReward_nullRaritySetsTitleRarityNull() {
            // given
            Title titleWithoutRarity = Title.builder()
                .name("희귀도없는칭호")
                .positionType(TitlePosition.RIGHT)
                .acquisitionType(TitleAcquisitionType.ACHIEVEMENT)
                .isActive(true)
                .build();
            setId(titleWithoutRarity, 150L);

            UpdateSeasonRankRewardAdminRequest request = new UpdateSeasonRankRewardAdminRequest(
                1, 1, null, null, 150L, "희귀도없는칭호", null, TitlePosition.RIGHT, 1
            );
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));
            when(rankRewardRepository.existsOverlappingRangeWithCategory(1L, null, 1, 1, 1L)).thenReturn(false);
            when(titleRepository.findById(150L)).thenReturn(Optional.of(titleWithoutRarity));

            // when
            seasonRankRewardAdminService.updateRankReward(1L, request);

            // then
            assertThat(testReward.getTitleRarity()).isNull();
        }
    }

    @Nested
    @DisplayName("deleteRankReward 테스트")
    class DeleteRankRewardTest {

        @Test
        @DisplayName("순위 보상을 비활성화한다")
        void deleteRankReward_success() {
            // given
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));

            // when
            seasonRankRewardAdminService.deleteRankReward(1L);

            // then
            assertThat(testReward.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("보상이 없으면 예외를 던진다")
        void deleteRankReward_notFound() {
            // given
            when(rankRewardRepository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonRankRewardAdminService.deleteRankReward(99L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("보상 설정을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("getRewardHistory 테스트")
    class GetRewardHistoryTest {

        @Test
        @DisplayName("시즌의 보상 이력을 페이지로 조회한다")
        void getRewardHistory_success() throws Exception {
            // given
            SeasonRewardHistory history = SeasonRewardHistory.builder()
                .seasonId(1L)
                .userId("user1")
                .finalRank(1)
                .totalExp(1000L)
                .titleId(100L)
                .titleName("챔피언")
                .status(SeasonRewardStatus.SUCCESS)
                .build();
            setId(history, 1L);

            Page<SeasonRewardHistory> page = new PageImpl<>(List.of(history));
            when(rewardHistoryRepository.findBySeasonIdOrderByFinalRankAsc(1L, PageRequest.of(0, 10)))
                .thenReturn(page);

            // when
            SeasonRewardHistoryAdminPageResponse result =
                seasonRankRewardAdminService.getRewardHistory(1L, PageRequest.of(0, 10));

            // then
            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content().get(0).userId()).isEqualTo("user1");
            assertThat(result.content().get(0).finalRank()).isEqualTo(1);
        }

        @Test
        @DisplayName("보상 이력이 없으면 빈 페이지를 반환한다")
        void getRewardHistory_empty() {
            // given
            Page<SeasonRewardHistory> emptyPage = new PageImpl<>(List.of());
            when(rewardHistoryRepository.findBySeasonIdOrderByFinalRankAsc(1L, PageRequest.of(0, 10)))
                .thenReturn(emptyPage);

            // when
            SeasonRewardHistoryAdminPageResponse result =
                seasonRankRewardAdminService.getRewardHistory(1L, PageRequest.of(0, 10));

            // then
            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("getRewardStats 테스트")
    class GetRewardStatsTest {

        @Test
        @DisplayName("시즌 보상 통계를 조회한다")
        void getRewardStats_success() {
            // given
            List<Object[]> stats = new ArrayList<>();
            stats.add(new Object[]{SeasonRewardStatus.SUCCESS, 10L});
            stats.add(new Object[]{SeasonRewardStatus.FAILED, 2L});
            stats.add(new Object[]{SeasonRewardStatus.SKIPPED, 3L});
            stats.add(new Object[]{SeasonRewardStatus.PENDING, 1L});
            when(rewardHistoryRepository.countBySeasonIdGroupByStatus(1L)).thenReturn(stats);

            // when
            SeasonRewardStatsAdminResponse result = seasonRankRewardAdminService.getRewardStats(1L);

            // then
            assertThat(result.seasonId()).isEqualTo(1L);
            assertThat(result.successCount()).isEqualTo(10);
            assertThat(result.failedCount()).isEqualTo(2);
            assertThat(result.skippedCount()).isEqualTo(3);
            assertThat(result.pendingCount()).isEqualTo(1);
            assertThat(result.totalCount()).isEqualTo(16);
            assertThat(result.isProcessed()).isTrue();
        }

        @Test
        @DisplayName("처리 이력이 없으면 isProcessed가 false다")
        void getRewardStats_notProcessed() {
            // given
            when(rewardHistoryRepository.countBySeasonIdGroupByStatus(1L)).thenReturn(List.of());

            // when
            SeasonRewardStatsAdminResponse result = seasonRankRewardAdminService.getRewardStats(1L);

            // then
            assertThat(result.seasonId()).isEqualTo(1L);
            assertThat(result.totalCount()).isZero();
            assertThat(result.isProcessed()).isFalse();
        }

        @Test
        @DisplayName("일부 상태만 있어도 누락된 상태는 0으로 처리한다")
        void getRewardStats_partialStatuses() {
            // given
            List<Object[]> stats = new ArrayList<>();
            stats.add(new Object[]{SeasonRewardStatus.SUCCESS, 5L});
            when(rewardHistoryRepository.countBySeasonIdGroupByStatus(1L)).thenReturn(stats);

            // when
            SeasonRewardStatsAdminResponse result = seasonRankRewardAdminService.getRewardStats(1L);

            // then
            assertThat(result.successCount()).isEqualTo(5);
            assertThat(result.failedCount()).isZero();
            assertThat(result.skippedCount()).isZero();
            assertThat(result.pendingCount()).isZero();
            assertThat(result.totalCount()).isEqualTo(5);
            assertThat(result.isProcessed()).isTrue();
        }
    }
}
