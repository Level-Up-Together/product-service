package io.pinkspider.leveluptogethermvp.gamificationservice.season.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.data.redis.core.RedisTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeasonAdminService 테스트")
class SeasonAdminServiceTest {

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplateForObject;

    @InjectMocks
    private SeasonAdminService seasonAdminService;

    private Season testSeason;
    private LocalDateTime now;
    private LocalDateTime start;
    private LocalDateTime end;

    @BeforeEach
    void setUp() throws Exception {
        now = LocalDateTime.now();
        start = now.minusDays(30);
        end = now.plusDays(30);

        testSeason = Season.builder()
            .title("테스트 시즌")
            .description("테스트 시즌 설명")
            .startAt(start)
            .endAt(end)
            .isActive(true)
            .sortOrder(0)
            .createdBy("admin")
            .modifiedBy("admin")
            .build();
        setId(testSeason, 1L);
    }

    @Nested
    @DisplayName("getAllSeasons 테스트")
    class GetAllSeasonsTest {

        @Test
        @DisplayName("모든 시즌 목록을 조회한다")
        void getAllSeasons_success() {
            // given
            when(seasonRepository.findAllByOrderBySortOrderAscStartAtDesc())
                .thenReturn(List.of(testSeason));

            // when
            List<SeasonAdminResponse> result = seasonAdminService.getAllSeasons();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(0).getTitle()).isEqualTo("테스트 시즌");
        }

        @Test
        @DisplayName("시즌이 없으면 빈 목록을 반환한다")
        void getAllSeasons_empty() {
            // given
            when(seasonRepository.findAllByOrderBySortOrderAscStartAtDesc())
                .thenReturn(List.of());

            // when
            List<SeasonAdminResponse> result = seasonAdminService.getAllSeasons();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchSeasons 테스트")
    class SearchSeasonsTest {

        @Test
        @DisplayName("키워드로 시즌을 검색한다")
        void searchSeasons_withKeyword() {
            // given
            Page<Season> page = new PageImpl<>(List.of(testSeason));
            when(seasonRepository.searchByKeyword("테스트", PageRequest.of(0, 10)))
                .thenReturn(page);

            // when
            SeasonAdminPageResponse result = seasonAdminService.searchSeasons("테스트", PageRequest.of(0, 10));

            // then
            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("키워드 없이 전체 시즌을 조회한다")
        void searchSeasons_withoutKeyword() {
            // given
            Page<Season> page = new PageImpl<>(List.of(testSeason));
            when(seasonRepository.findAllByOrderBySortOrderAscStartAtDesc(PageRequest.of(0, 10)))
                .thenReturn(page);

            // when
            SeasonAdminPageResponse result = seasonAdminService.searchSeasons(null, PageRequest.of(0, 10));

            // then
            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("빈 키워드일 때 전체 시즌을 조회한다")
        void searchSeasons_withBlankKeyword() {
            // given
            Page<Season> page = new PageImpl<>(List.of(testSeason));
            when(seasonRepository.findAllByOrderBySortOrderAscStartAtDesc(PageRequest.of(0, 10)))
                .thenReturn(page);

            // when
            SeasonAdminPageResponse result = seasonAdminService.searchSeasons("   ", PageRequest.of(0, 10));

            // then
            assertThat(result.content()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getSeason 테스트")
    class GetSeasonTest {

        @Test
        @DisplayName("ID로 시즌을 조회한다")
        void getSeason_success() {
            // given
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));

            // when
            SeasonAdminResponse result = seasonAdminService.getSeason(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("테스트 시즌");
        }

        @Test
        @DisplayName("시즌이 없으면 예외를 던진다")
        void getSeason_notFound() {
            // given
            when(seasonRepository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonAdminService.getSeason(99L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시즌을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("getCurrentSeason 테스트")
    class GetCurrentSeasonTest {

        @Test
        @DisplayName("현재 시즌을 조회한다")
        void getCurrentSeason_success() {
            // given
            when(seasonRepository.findCurrentSeason(any(LocalDateTime.class)))
                .thenReturn(Optional.of(testSeason));

            // when
            SeasonAdminResponse result = seasonAdminService.getCurrentSeason();

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("현재 활성 시즌이 없으면 null을 반환한다")
        void getCurrentSeason_returnsNullWhenNotFound() {
            // given
            when(seasonRepository.findCurrentSeason(any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

            // when
            SeasonAdminResponse result = seasonAdminService.getCurrentSeason();

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getUpcomingSeasons 테스트")
    class GetUpcomingSeasonsTest {

        @Test
        @DisplayName("예정된 시즌 목록을 조회한다")
        void getUpcomingSeasons_success() {
            // given
            Season upcomingSeason = Season.builder()
                .title("다가오는 시즌")
                .startAt(now.plusDays(10))
                .endAt(now.plusDays(40))
                .isActive(true)
                .build();
            setId(upcomingSeason, 2L);

            when(seasonRepository.findUpcomingSeasons(any(LocalDateTime.class)))
                .thenReturn(List.of(upcomingSeason));

            // when
            List<SeasonAdminResponse> result = seasonAdminService.getUpcomingSeasons();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isEqualTo("다가오는 시즌");
        }

        @Test
        @DisplayName("예정 시즌이 없으면 빈 목록을 반환한다")
        void getUpcomingSeasons_empty() {
            // given
            when(seasonRepository.findUpcomingSeasons(any(LocalDateTime.class)))
                .thenReturn(List.of());

            // when
            List<SeasonAdminResponse> result = seasonAdminService.getUpcomingSeasons();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("createSeason 테스트")
    class CreateSeasonTest {

        @Test
        @DisplayName("시즌을 생성한다")
        void createSeason_success() {
            // given
            SeasonAdminRequest request = new SeasonAdminRequest(
                "신규 시즌", "설명", start, end, true, null, null, 0, "admin", "admin"
            );
            when(seasonRepository.existsOverlappingActiveSeasonForNew(start, end)).thenReturn(false);
            when(seasonRepository.save(any(Season.class))).thenReturn(testSeason);
            when(redisTemplateForObject.keys(any())).thenReturn(null);

            // when
            SeasonAdminResponse result = seasonAdminService.createSeason(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("테스트 시즌");
            verify(seasonRepository).save(any(Season.class));
        }

        @Test
        @DisplayName("isActive가 null이면 기본값 true로 생성한다")
        void createSeason_isActiveNullDefaultsToTrue() {
            // given
            SeasonAdminRequest request = new SeasonAdminRequest(
                "신규 시즌", "설명", start, end, null, null, null, null, "admin", "admin"
            );
            when(seasonRepository.existsOverlappingActiveSeasonForNew(start, end)).thenReturn(false);
            when(seasonRepository.save(any(Season.class))).thenReturn(testSeason);
            when(redisTemplateForObject.keys(any())).thenReturn(null);

            // when
            SeasonAdminResponse result = seasonAdminService.createSeason(request);

            // then
            assertThat(result).isNotNull();
            verify(seasonRepository).save(any(Season.class));
        }

        @Test
        @DisplayName("isActive가 false이면 중복 활성 시즌 검사를 생략한다")
        void createSeason_inactiveSkipsOverlapCheck() {
            // given
            SeasonAdminRequest request = new SeasonAdminRequest(
                "비활성 시즌", "설명", start, end, false, null, null, 0, "admin", "admin"
            );
            when(seasonRepository.save(any(Season.class))).thenReturn(testSeason);
            when(redisTemplateForObject.keys(any())).thenReturn(null);

            // when
            SeasonAdminResponse result = seasonAdminService.createSeason(request);

            // then
            assertThat(result).isNotNull();
            verify(seasonRepository, never()).existsOverlappingActiveSeasonForNew(any(), any());
        }

        @Test
        @DisplayName("종료 일시가 시작 일시 이전이면 예외를 던진다")
        void createSeason_invalidDates() {
            // given
            SeasonAdminRequest request = new SeasonAdminRequest(
                "잘못된 시즌", "설명", end, start, true, null, null, 0, "admin", "admin"
            );

            // when & then
            assertThatThrownBy(() -> seasonAdminService.createSeason(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("종료 일시는 시작 일시 이후여야 합니다");
        }

        @Test
        @DisplayName("종료 일시와 시작 일시가 같으면 예외를 던진다")
        void createSeason_sameDateThrowsException() {
            // given
            LocalDateTime sameTime = now;
            SeasonAdminRequest request = new SeasonAdminRequest(
                "잘못된 시즌", "설명", sameTime, sameTime, true, null, null, 0, "admin", "admin"
            );

            // when & then
            assertThatThrownBy(() -> seasonAdminService.createSeason(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("종료 일시는 시작 일시 이후여야 합니다");
        }

        @Test
        @DisplayName("활성 시즌 기간이 겹치면 예외를 던진다")
        void createSeason_overlappingActiveSeason() {
            // given
            SeasonAdminRequest request = new SeasonAdminRequest(
                "겹치는 시즌", "설명", start, end, true, null, null, 0, "admin", "admin"
            );
            when(seasonRepository.existsOverlappingActiveSeasonForNew(start, end)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> seasonAdminService.createSeason(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 기간에 이미 활성화된 시즌이 존재합니다");
        }

        @Test
        @DisplayName("생성 시 Redis 캐시를 삭제한다")
        void createSeason_evictsCache() {
            // given
            SeasonAdminRequest request = new SeasonAdminRequest(
                "신규 시즌", "설명", start, end, true, null, null, 0, "admin", "admin"
            );
            when(seasonRepository.existsOverlappingActiveSeasonForNew(start, end)).thenReturn(false);
            when(seasonRepository.save(any(Season.class))).thenReturn(testSeason);
            when(redisTemplateForObject.keys("currentSeason*")).thenReturn(Set.of("currentSeason::ko"));
            when(redisTemplateForObject.keys("seasonMvpData*")).thenReturn(null);

            // when
            seasonAdminService.createSeason(request);

            // then
            verify(redisTemplateForObject).delete(Set.of("currentSeason::ko"));
        }
    }

    @Nested
    @DisplayName("updateSeason 테스트")
    class UpdateSeasonTest {

        @Test
        @DisplayName("시즌을 수정한다")
        void updateSeason_success() {
            // given
            LocalDateTime newStart = now.minusDays(20);
            LocalDateTime newEnd = now.plusDays(40);
            SeasonAdminRequest request = new SeasonAdminRequest(
                "수정된 시즌", "수정 설명", newStart, newEnd, true, null, null, 1, null, "admin"
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRepository.existsOverlappingActiveSeason(newStart, newEnd, 1L)).thenReturn(false);
            when(seasonRepository.save(any(Season.class))).thenReturn(testSeason);
            when(redisTemplateForObject.keys(any())).thenReturn(null);

            // when
            SeasonAdminResponse result = seasonAdminService.updateSeason(1L, request);

            // then
            assertThat(result).isNotNull();
            verify(seasonRepository).save(any(Season.class));
        }

        @Test
        @DisplayName("시즌이 없으면 예외를 던진다")
        void updateSeason_notFound() {
            // given
            SeasonAdminRequest request = new SeasonAdminRequest(
                "수정된 시즌", "수정 설명", start, end, true, null, null, 0, null, "admin"
            );
            when(seasonRepository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonAdminService.updateSeason(99L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시즌을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("수정 시 isActive가 null이면 기존 값을 유지한다")
        void updateSeason_isActiveNullPreservesExistingValue() {
            // given
            SeasonAdminRequest request = new SeasonAdminRequest(
                "수정된 시즌", "수정 설명", start, end, null, null, null, null, null, "admin"
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRepository.existsOverlappingActiveSeason(start, end, 1L)).thenReturn(false);
            when(seasonRepository.save(any(Season.class))).thenReturn(testSeason);
            when(redisTemplateForObject.keys(any())).thenReturn(null);

            // when
            seasonAdminService.updateSeason(1L, request);

            // then - testSeason.isActive == true, 중복 검사가 실행되어야 함
            verify(seasonRepository).existsOverlappingActiveSeason(start, end, 1L);
        }

        @Test
        @DisplayName("종료 일시가 시작 일시보다 이르면 예외를 던진다")
        void updateSeason_invalidDates() {
            // given
            SeasonAdminRequest request = new SeasonAdminRequest(
                "수정 시즌", "설명", end, start, true, null, null, 0, null, "admin"
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));

            // when & then
            assertThatThrownBy(() -> seasonAdminService.updateSeason(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("종료 일시는 시작 일시 이후여야 합니다");
        }

        @Test
        @DisplayName("수정 시 활성 시즌이 겹치면 예외를 던진다")
        void updateSeason_overlappingActiveSeason() {
            // given
            SeasonAdminRequest request = new SeasonAdminRequest(
                "겹치는 시즌", "설명", start, end, true, null, null, 0, null, "admin"
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRepository.existsOverlappingActiveSeason(start, end, 1L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> seasonAdminService.updateSeason(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 기간에 이미 활성화된 시즌이 존재합니다");
        }

        @Test
        @DisplayName("sortOrder가 null이면 변경하지 않는다")
        void updateSeason_nullSortOrderNotChanged() {
            // given
            SeasonAdminRequest request = new SeasonAdminRequest(
                "수정된 시즌", "수정 설명", start, end, false, null, null, null, null, "admin"
            );
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRepository.save(any(Season.class))).thenReturn(testSeason);
            when(redisTemplateForObject.keys(any())).thenReturn(null);

            // when
            seasonAdminService.updateSeason(1L, request);

            // then
            verify(seasonRepository).save(any(Season.class));
        }
    }

    @Nested
    @DisplayName("deleteSeason 테스트")
    class DeleteSeasonTest {

        @Test
        @DisplayName("시즌을 삭제한다")
        void deleteSeason_success() {
            // given
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(redisTemplateForObject.keys(any())).thenReturn(null);

            // when
            seasonAdminService.deleteSeason(1L);

            // then
            verify(seasonRepository).delete(testSeason);
        }

        @Test
        @DisplayName("시즌이 없으면 예외를 던진다")
        void deleteSeason_notFound() {
            // given
            when(seasonRepository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonAdminService.deleteSeason(99L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시즌을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("삭제 시 Redis 캐시를 삭제한다")
        void deleteSeason_evictsCache() {
            // given
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(redisTemplateForObject.keys("currentSeason*")).thenReturn(Set.of("currentSeason::ko"));
            when(redisTemplateForObject.keys("seasonMvpData*")).thenReturn(Set.of("seasonMvpData::ko"));

            // when
            seasonAdminService.deleteSeason(1L);

            // then
            verify(redisTemplateForObject).delete(Set.of("currentSeason::ko"));
            verify(redisTemplateForObject).delete(Set.of("seasonMvpData::ko"));
        }
    }

    @Nested
    @DisplayName("toggleActive 테스트")
    class ToggleActiveTest {

        @Test
        @DisplayName("활성 시즌을 비활성으로 토글한다")
        void toggleActive_fromActiveToInactive() {
            // given
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRepository.save(any(Season.class))).thenReturn(testSeason);
            when(redisTemplateForObject.keys(any())).thenReturn(null);

            // testSeason.isActive == true
            // when
            seasonAdminService.toggleActive(1L);

            // then
            assertThat(testSeason.getIsActive()).isFalse();
            verify(seasonRepository).save(any(Season.class));
        }

        @Test
        @DisplayName("비활성 시즌을 활성으로 토글할 때 중복 검사를 한다")
        void toggleActive_fromInactiveToActive() throws Exception {
            // given
            Season inactiveSeason = Season.builder()
                .title("비활성 시즌")
                .startAt(start)
                .endAt(end)
                .isActive(false)
                .build();
            setId(inactiveSeason, 2L);

            when(seasonRepository.findById(2L)).thenReturn(Optional.of(inactiveSeason));
            when(seasonRepository.existsOverlappingActiveSeason(start, end, 2L)).thenReturn(false);
            when(seasonRepository.save(any(Season.class))).thenReturn(inactiveSeason);
            when(redisTemplateForObject.keys(any())).thenReturn(null);

            // when
            seasonAdminService.toggleActive(2L);

            // then
            assertThat(inactiveSeason.getIsActive()).isTrue();
            verify(seasonRepository).existsOverlappingActiveSeason(start, end, 2L);
        }

        @Test
        @DisplayName("비활성 시즌을 활성화할 때 겹치는 시즌이 있으면 예외를 던진다")
        void toggleActive_overlappingActiveSeason() throws Exception {
            // given
            Season inactiveSeason = Season.builder()
                .title("비활성 시즌")
                .startAt(start)
                .endAt(end)
                .isActive(false)
                .build();
            setId(inactiveSeason, 2L);

            when(seasonRepository.findById(2L)).thenReturn(Optional.of(inactiveSeason));
            when(seasonRepository.existsOverlappingActiveSeason(start, end, 2L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> seasonAdminService.toggleActive(2L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 기간에 이미 활성화된 시즌이 존재합니다");
        }

        @Test
        @DisplayName("시즌이 없으면 예외를 던진다")
        void toggleActive_notFound() {
            // given
            when(seasonRepository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seasonAdminService.toggleActive(99L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("시즌을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("토글 시 Redis 캐시를 삭제한다")
        void toggleActive_evictsCache() {
            // given
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRepository.save(any(Season.class))).thenReturn(testSeason);
            when(redisTemplateForObject.keys("currentSeason*")).thenReturn(Set.of("currentSeason::ko"));
            when(redisTemplateForObject.keys("seasonMvpData*")).thenReturn(null);

            // when
            seasonAdminService.toggleActive(1L);

            // then
            verify(redisTemplateForObject).delete(Set.of("currentSeason::ko"));
        }

        @Test
        @DisplayName("Redis 캐시 삭제 실패 시 예외를 무시하고 계속 진행한다")
        void toggleActive_cacheEvictionFailureSilentlyIgnored() {
            // given
            when(seasonRepository.findById(1L)).thenReturn(Optional.of(testSeason));
            when(seasonRepository.save(any(Season.class))).thenReturn(testSeason);
            when(redisTemplateForObject.keys(any())).thenThrow(new RuntimeException("Redis 연결 실패"));

            // when & then - 예외가 전파되지 않아야 함
            seasonAdminService.toggleActive(1L);
        }
    }
}
