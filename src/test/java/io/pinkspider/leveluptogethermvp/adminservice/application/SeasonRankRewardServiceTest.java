package io.pinkspider.leveluptogethermvp.adminservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.CreateSeasonRankRewardRequest;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.SeasonRankRewardResponse;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.UpdateSeasonRankRewardRequest;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.SeasonRankReward;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.SeasonRankRewardRepository;
import io.pinkspider.leveluptogethermvp.adminservice.infrastructure.SeasonRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitlePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
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
            CreateSeasonRankRewardRequest request = new CreateSeasonRankRewardRequest(1, 1, 100L, 1);
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRange(anyLong(), anyInt(), anyInt(), anyLong())).thenReturn(false);
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
            CreateSeasonRankRewardRequest request = new CreateSeasonRankRewardRequest(1, 1, 100L, 1);
            when(seasonRepository.findById(1L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonRankRewardService.createRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시즌을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("시작 순위가 종료 순위보다 크면 예외를 던진다")
        void failWhenInvalidRankRange() {
            // given
            CreateSeasonRankRewardRequest request = new CreateSeasonRankRewardRequest(10, 1, 100L, 1);
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));

            // when & then
            assertThatThrownBy(() -> seasonRankRewardService.createRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시작 순위가 종료 순위보다 클 수 없습니다");
        }

        @Test
        @DisplayName("순위 구간이 중복되면 예외를 던진다")
        void failWhenRankRangeOverlap() {
            // given
            CreateSeasonRankRewardRequest request = new CreateSeasonRankRewardRequest(1, 5, 100L, 1);
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(rankRewardRepository.existsOverlappingRange(1L, 1, 5, 0L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> seasonRankRewardService.createRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("순위 구간이 기존 보상과 중복됩니다");
        }
    }

    @Nested
    @DisplayName("updateRankReward")
    class UpdateRankRewardTest {

        @Test
        @DisplayName("순위별 보상을 수정한다")
        void success() {
            // given
            UpdateSeasonRankRewardRequest request = new UpdateSeasonRankRewardRequest(1, 3, 100L, 1);
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.of(testReward));
            when(rankRewardRepository.existsOverlappingRange(1L, 1, 3, 1L)).thenReturn(false);
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
            UpdateSeasonRankRewardRequest request = new UpdateSeasonRankRewardRequest(1, 3, 100L, 1);
            when(rankRewardRepository.findById(1L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonRankRewardService.updateRankReward(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("보상 설정을 찾을 수 없습니다");
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
}
