package io.pinkspider.leveluptogethermvp.gamificationservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.DailyMvpHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DailyMvpCategoryStatsRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DailyMvpHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DailyMvpHistoryServiceTest {

    @Mock
    private DailyMvpHistoryRepository historyRepository;

    @Mock
    private DailyMvpCategoryStatsRepository categoryStatsRepository;

    @Mock
    private ExperienceHistoryRepository experienceHistoryRepository;

    @Mock
    private UserQueryFacade userQueryFacadeService;

    @Mock
    private UserExperienceRepository userExperienceRepository;

    @Mock
    private UserTitleRepository userTitleRepository;

    @Mock
    private MissionCategoryService missionCategoryService;

    @InjectMocks
    private DailyMvpHistoryService dailyMvpHistoryService;

    private static final LocalDate TEST_DATE = LocalDate.of(2025, 1, 15);

    // ===================== 헬퍼 메서드 =====================

    /**
     * [userId, earnedExp] 형태의 Object[] 생성
     */
    private List<Object[]> topGainersOf(Object[]... rows) {
        List<Object[]> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(row);
        }
        return result;
    }

    private Object[] topGainerRow(String userId, long earnedExp) {
        return new Object[]{userId, earnedExp};
    }

    /**
     * [categoryName, categoryId, exp, activityCount] 형태의 Object[] 생성
     */
    private Object[] categoryStatRow(String categoryName, String categoryId, long exp, int activityCount) {
        return new Object[]{categoryName, categoryId, exp, activityCount};
    }

    private List<Object[]> categoryStatsOf(Object[]... rows) {
        List<Object[]> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(row);
        }
        return result;
    }

    private UserExperience createUserExperience(String userId, int level) {
        UserExperience ue = UserExperience.builder()
            .userId(userId)
            .currentLevel(level)
            .currentExp(0)
            .totalExp(0)
            .build();
        setId(ue, 1L);
        return ue;
    }

    private Title createTitle(Long id, String name, TitleRarity rarity, TitlePosition position) {
        Title title = Title.builder()
            .name(name)
            .rarity(rarity)
            .positionType(position)
            .isActive(true)
            .build();
        setId(title, id);
        return title;
    }

    private UserTitle createUserTitle(Long id, String userId, Title title, TitlePosition position) {
        UserTitle userTitle = UserTitle.builder()
            .userId(userId)
            .title(title)
            .isEquipped(true)
            .equippedPosition(position)
            .build();
        setId(userTitle, id);
        return userTitle;
    }

    private MissionCategoryResponse createCategoryResponse(Long id, String name) {
        return MissionCategoryResponse.builder()
            .id(id)
            .name(name)
            .isActive(true)
            .build();
    }

    private UserProfileInfo createUserProfileInfo(String userId, String nickname) {
        return new UserProfileInfo(userId, nickname, "https://example.com/picture.jpg", 5, null, null, null);
    }

    // ===================== captureAndSaveDailyMvp 테스트 =====================

    @Nested
    @DisplayName("captureAndSaveDailyMvp 테스트")
    class CaptureAndSaveDailyMvpTest {

        @Test
        @DisplayName("이미 5개 이상의 MVP 히스토리가 저장된 경우 스킵한다")
        void captureAndSaveDailyMvp_alreadySaved_skips() {
            // given
            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(5L);

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            verify(historyRepository, never()).deleteByMvpDate(any());
            verify(experienceHistoryRepository, never()).findTopExpGainersByPeriod(any(), any(), any());
            verify(historyRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 MVP_COUNT(5)보다 많은 경우에도 스킵한다")
        void captureAndSaveDailyMvp_moreThanMvpCount_skips() {
            // given
            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(7L);

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            verify(historyRepository, never()).deleteByMvpDate(any());
            verify(historyRepository, never()).save(any());
        }

        @Test
        @DisplayName("부분적으로 저장된 경우(0 < count < 5) 기존 데이터를 삭제 후 재저장한다")
        void captureAndSaveDailyMvp_partialSaved_deletesAndResaves() {
            // given
            String userId = "user-1";
            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(3L);

            List<Object[]> topGainers = topGainersOf(topGainerRow(userId, 500L));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(userId, createUserProfileInfo(userId, "테스터")));
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(List.of(createUserExperience(userId, 10)));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories())
                .thenReturn(Collections.emptyList());
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            verify(historyRepository).deleteByMvpDate(TEST_DATE);
            verify(categoryStatsRepository).deleteByStatsDate(TEST_DATE);
            verify(historyRepository).save(any(DailyMvpHistory.class));
        }

        @Test
        @DisplayName("MVP 데이터가 없는 경우(topGainers가 빈 리스트) 저장 없이 종료한다")
        void captureAndSaveDailyMvp_noData_doesNotSave() {
            // given
            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(0L);
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            verify(historyRepository, never()).save(any());
            verify(categoryStatsRepository, never()).save(any());
        }

        @Test
        @DisplayName("MVP 데이터가 존재하면 순위 순서대로 히스토리를 저장한다")
        void captureAndSaveDailyMvp_success() {
            // given
            String userId1 = "user-1";
            String userId2 = "user-2";

            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(0L);

            List<Object[]> topGainers = topGainersOf(
                topGainerRow(userId1, 1000L),
                topGainerRow(userId2, 800L)
            );
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(
                    userId1, createUserProfileInfo(userId1, "유저1"),
                    userId2, createUserProfileInfo(userId2, "유저2")
                ));
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(List.of(
                    createUserExperience(userId1, 15),
                    createUserExperience(userId2, 12)
                ));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId1), any(), any()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId2), any(), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories())
                .thenReturn(Collections.emptyList());
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            ArgumentCaptor<DailyMvpHistory> captor = ArgumentCaptor.forClass(DailyMvpHistory.class);
            verify(historyRepository, times(2)).save(captor.capture());

            List<DailyMvpHistory> saved = captor.getAllValues();
            assertThat(saved).hasSize(2);
            assertThat(saved.get(0).getMvpRank()).isEqualTo(1);
            assertThat(saved.get(0).getUserId()).isEqualTo(userId1);
            assertThat(saved.get(0).getEarnedExp()).isEqualTo(1000L);
            assertThat(saved.get(0).getMvpDate()).isEqualTo(TEST_DATE);
            assertThat(saved.get(0).getNickname()).isEqualTo("유저1");
            assertThat(saved.get(0).getUserLevel()).isEqualTo(15);

            assertThat(saved.get(1).getMvpRank()).isEqualTo(2);
            assertThat(saved.get(1).getUserId()).isEqualTo(userId2);
            assertThat(saved.get(1).getEarnedExp()).isEqualTo(800L);
        }

        @Test
        @DisplayName("유저 프로필이 없는 경우 nickname과 picture를 null로 저장한다")
        void captureAndSaveDailyMvp_noProfile_savesNullNickname() {
            // given
            String userId = "user-no-profile";

            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(0L);

            List<Object[]> topGainers = topGainersOf(topGainerRow(userId, 300L));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            // profileMap에 해당 userId 없음
            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Collections.emptyMap());
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories())
                .thenReturn(Collections.emptyList());
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            ArgumentCaptor<DailyMvpHistory> captor = ArgumentCaptor.forClass(DailyMvpHistory.class);
            verify(historyRepository).save(captor.capture());

            DailyMvpHistory saved = captor.getValue();
            assertThat(saved.getNickname()).isNull();
            assertThat(saved.getPicture()).isNull();
        }

        @Test
        @DisplayName("레벨 정보가 없는 경우 기본값 1로 저장한다")
        void captureAndSaveDailyMvp_noLevelInfo_usesDefaultLevel() {
            // given
            String userId = "user-no-level";

            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(0L);

            List<Object[]> topGainers = topGainersOf(topGainerRow(userId, 300L));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(userId, createUserProfileInfo(userId, "레벨없는유저")));
            // levelMap 비어있음 - getOrDefault(userId, 1) → 1
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories())
                .thenReturn(Collections.emptyList());
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            ArgumentCaptor<DailyMvpHistory> captor = ArgumentCaptor.forClass(DailyMvpHistory.class);
            verify(historyRepository).save(captor.capture());

            DailyMvpHistory saved = captor.getValue();
            assertThat(saved.getUserLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("LEFT+RIGHT 칭호가 모두 장착된 경우 조합된 칭호명과 가장 높은 등급으로 저장한다")
        void captureAndSaveDailyMvp_withLeftAndRightTitle_savesCombinedTitleName() {
            // given
            String userId = "user-with-title";

            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(0L);

            List<Object[]> topGainers = topGainersOf(topGainerRow(userId, 500L));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            Title leftTitle = createTitle(1L, "용감한", TitleRarity.EPIC, TitlePosition.LEFT);
            Title rightTitle = createTitle(2L, "전사", TitleRarity.RARE, TitlePosition.RIGHT);
            UserTitle leftUserTitle = createUserTitle(1L, userId, leftTitle, TitlePosition.LEFT);
            UserTitle rightUserTitle = createUserTitle(2L, userId, rightTitle, TitlePosition.RIGHT);

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(userId, createUserProfileInfo(userId, "용사")));
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(List.of(createUserExperience(userId, 20)));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(List.of(leftUserTitle, rightUserTitle));
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories())
                .thenReturn(Collections.emptyList());
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            ArgumentCaptor<DailyMvpHistory> captor = ArgumentCaptor.forClass(DailyMvpHistory.class);
            verify(historyRepository).save(captor.capture());

            DailyMvpHistory saved = captor.getValue();
            assertThat(saved.getTitleName()).isEqualTo("용감한 전사");
            // EPIC(ordinal=3) > RARE(ordinal=2) → 최고 등급 EPIC
            assertThat(saved.getTitleRarity()).isEqualTo(TitleRarity.EPIC);
        }

        @Test
        @DisplayName("LEFT 칭호만 장착된 경우 LEFT 칭호명으로 저장한다")
        void captureAndSaveDailyMvp_withLeftTitleOnly_savesLeftTitleName() {
            // given
            String userId = "user-left-only";

            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(0L);

            List<Object[]> topGainers = topGainersOf(topGainerRow(userId, 500L));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            Title leftTitle = createTitle(1L, "용감한", TitleRarity.RARE, TitlePosition.LEFT);
            UserTitle leftUserTitle = createUserTitle(1L, userId, leftTitle, TitlePosition.LEFT);

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(userId, createUserProfileInfo(userId, "유저")));
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(List.of(createUserExperience(userId, 10)));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(List.of(leftUserTitle));
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories())
                .thenReturn(Collections.emptyList());
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            ArgumentCaptor<DailyMvpHistory> captor = ArgumentCaptor.forClass(DailyMvpHistory.class);
            verify(historyRepository).save(captor.capture());

            DailyMvpHistory saved = captor.getValue();
            assertThat(saved.getTitleName()).isEqualTo("용감한");
            assertThat(saved.getTitleRarity()).isEqualTo(TitleRarity.RARE);
        }

        @Test
        @DisplayName("칭호가 없는 경우 titleName과 titleRarity를 null로 저장한다")
        void captureAndSaveDailyMvp_noTitle_savesNullTitle() {
            // given
            String userId = "user-no-title";

            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(0L);

            List<Object[]> topGainers = topGainersOf(topGainerRow(userId, 400L));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(userId, createUserProfileInfo(userId, "칭호없는유저")));
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(List.of(createUserExperience(userId, 5)));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories())
                .thenReturn(Collections.emptyList());
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            ArgumentCaptor<DailyMvpHistory> captor = ArgumentCaptor.forClass(DailyMvpHistory.class);
            verify(historyRepository).save(captor.capture());

            DailyMvpHistory saved = captor.getValue();
            assertThat(saved.getTitleName()).isNull();
            assertThat(saved.getTitleRarity()).isNull();
        }

        @Test
        @DisplayName("카테고리 통계가 있는 경우 경험치가 가장 높은 카테고리를 저장한다")
        void captureAndSaveDailyMvp_withCategoryStats_savesTopCategory() {
            // given
            String userId = "user-with-category";
            String categoryName = "운동";
            Long categoryId = 10L;

            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(0L);

            List<Object[]> topGainers = topGainersOf(topGainerRow(userId, 700L));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            // categoryStats: 이미 경험치 순으로 정렬된 상태 (findUserCategoryExpByPeriod 쿼리에서 ORDER BY totalExp DESC)
            // [categoryName, categoryId(unused), exp, activityCount]
            List<Object[]> categoryStats = categoryStatsOf(
                categoryStatRow(categoryName, "운동", 500L, 3),
                categoryStatRow("독서", "독서", 200L, 2)
            );

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(userId, createUserProfileInfo(userId, "카테고리유저")));
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(List.of(createUserExperience(userId, 8)));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId), any(), any()))
                .thenReturn(categoryStats);
            when(missionCategoryService.getActiveCategories())
                .thenReturn(List.of(createCategoryResponse(categoryId, categoryName)));
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            ArgumentCaptor<DailyMvpHistory> captor = ArgumentCaptor.forClass(DailyMvpHistory.class);
            verify(historyRepository).save(captor.capture());

            DailyMvpHistory saved = captor.getValue();
            assertThat(saved.getTopCategoryName()).isEqualTo(categoryName);
            assertThat(saved.getTopCategoryId()).isEqualTo(categoryId);
            assertThat(saved.getTopCategoryExp()).isEqualTo(500L);
        }

        @Test
        @DisplayName("카테고리 통계가 없는 경우 topCategoryName, topCategoryId를 null로 저장한다")
        void captureAndSaveDailyMvp_noCategoryStats_savesNullCategory() {
            // given
            String userId = "user-no-category";

            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(0L);

            List<Object[]> topGainers = topGainersOf(topGainerRow(userId, 300L));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(userId, createUserProfileInfo(userId, "유저")));
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(List.of(createUserExperience(userId, 5)));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories())
                .thenReturn(Collections.emptyList());
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            ArgumentCaptor<DailyMvpHistory> captor = ArgumentCaptor.forClass(DailyMvpHistory.class);
            verify(historyRepository).save(captor.capture());

            DailyMvpHistory saved = captor.getValue();
            assertThat(saved.getTopCategoryName()).isNull();
            assertThat(saved.getTopCategoryId()).isNull();
            assertThat(saved.getTopCategoryExp()).isEqualTo(0L);
        }

        @Test
        @DisplayName("카테고리 통계가 있을 때 DailyMvpCategoryStats도 저장한다")
        void captureAndSaveDailyMvp_withCategoryStats_savesCategoryStatsRecords() {
            // given
            String userId = "user-category-stats";
            String categoryName = "운동";
            Long categoryId = 10L;

            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(0L);

            List<Object[]> topGainers = topGainersOf(topGainerRow(userId, 600L));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            List<Object[]> categoryStats = categoryStatsOf(
                categoryStatRow(categoryName, "운동", 600L, 4)
            );

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(userId, createUserProfileInfo(userId, "유저")));
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(List.of(createUserExperience(userId, 7)));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId), any(), any()))
                .thenReturn(categoryStats);
            when(missionCategoryService.getActiveCategories())
                .thenReturn(List.of(createCategoryResponse(categoryId, categoryName)));
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            verify(categoryStatsRepository).save(any());
        }

        @Test
        @DisplayName("카테고리 ID를 찾을 수 없는 경우 카테고리 통계를 저장하지 않는다")
        void captureAndSaveDailyMvp_categoryIdNotFound_doesNotSaveCategoryStats() {
            // given
            String userId = "user-unknown-category";

            when(historyRepository.countByMvpDate(TEST_DATE)).thenReturn(0L);

            List<Object[]> topGainers = topGainersOf(topGainerRow(userId, 400L));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            // 카테고리 이름이 "존재하지않는카테고리"이고 missionCategoryService에는 없음
            List<Object[]> categoryStats = categoryStatsOf(
                categoryStatRow("존재하지않는카테고리", "unknown", 400L, 2)
            );

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(userId, createUserProfileInfo(userId, "유저")));
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(List.of(createUserExperience(userId, 5)));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId), any(), any()))
                .thenReturn(categoryStats);
            // categoryNameToIdMap에 "존재하지않는카테고리"가 없음
            when(missionCategoryService.getActiveCategories())
                .thenReturn(List.of(createCategoryResponse(1L, "운동")));
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.captureAndSaveDailyMvp(TEST_DATE);

            // then
            verify(categoryStatsRepository, never()).save(any());
        }
    }

    // ===================== reprocessDailyMvp 테스트 =====================

    @Nested
    @DisplayName("reprocessDailyMvp 테스트")
    class ReprocessDailyMvpTest {

        @Test
        @DisplayName("기존 데이터를 삭제한 후 재처리하여 저장한다")
        void reprocessDailyMvp_deletesExistingAndReprocesses() {
            // given
            String userId = "user-1";

            List<Object[]> topGainers = topGainersOf(topGainerRow(userId, 500L));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(userId, createUserProfileInfo(userId, "유저1")));
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(List.of(createUserExperience(userId, 10)));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories())
                .thenReturn(Collections.emptyList());
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.reprocessDailyMvp(TEST_DATE);

            // then
            verify(historyRepository).deleteByMvpDate(TEST_DATE);
            verify(categoryStatsRepository).deleteByStatsDate(TEST_DATE);
            verify(historyRepository).save(any(DailyMvpHistory.class));
        }

        @Test
        @DisplayName("재처리 시 데이터가 없는 경우 삭제만 하고 저장하지 않는다")
        void reprocessDailyMvp_noData_onlyDeletes() {
            // given
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

            // when
            dailyMvpHistoryService.reprocessDailyMvp(TEST_DATE);

            // then
            verify(historyRepository).deleteByMvpDate(TEST_DATE);
            verify(categoryStatsRepository).deleteByStatsDate(TEST_DATE);
            verify(historyRepository, never()).save(any());
        }

        @Test
        @DisplayName("재처리는 기존 count와 관계없이 항상 삭제 후 저장한다")
        void reprocessDailyMvp_ignoresExistingCount() {
            // given
            String userId = "user-reprocess";

            List<Object[]> topGainers = topGainersOf(topGainerRow(userId, 600L));
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(userId, createUserProfileInfo(userId, "재처리유저")));
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(List.of(createUserExperience(userId, 12)));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories())
                .thenReturn(Collections.emptyList());
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.reprocessDailyMvp(TEST_DATE);

            // then: countByMvpDate가 호출되지 않음을 검증 (reprocess는 존재 체크 없이 무조건 재처리)
            verify(historyRepository, never()).countByMvpDate(any());
            verify(historyRepository).deleteByMvpDate(TEST_DATE);
            verify(historyRepository).save(any(DailyMvpHistory.class));
        }

        @Test
        @DisplayName("재처리 시 여러 MVP 유저가 있는 경우 순위 순서대로 각각 저장한다")
        void reprocessDailyMvp_multipleUsers_savesAll() {
            // given
            String userId1 = "user-1";
            String userId2 = "user-2";
            String userId3 = "user-3";

            List<Object[]> topGainers = topGainersOf(
                topGainerRow(userId1, 1000L),
                topGainerRow(userId2, 800L),
                topGainerRow(userId3, 600L)
            );
            when(experienceHistoryRepository.findTopExpGainersByPeriod(any(), any(), any(PageRequest.class)))
                .thenReturn(topGainers);

            when(userQueryFacadeService.getUserProfiles(anyList()))
                .thenReturn(Map.of(
                    userId1, createUserProfileInfo(userId1, "유저1"),
                    userId2, createUserProfileInfo(userId2, "유저2"),
                    userId3, createUserProfileInfo(userId3, "유저3")
                ));
            when(userExperienceRepository.findByUserIdIn(anyList()))
                .thenReturn(List.of(
                    createUserExperience(userId1, 20),
                    createUserExperience(userId2, 15),
                    createUserExperience(userId3, 10)
                ));
            when(userTitleRepository.findEquippedTitlesByUserIdIn(anyList()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId1), any(), any()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId2), any(), any()))
                .thenReturn(Collections.emptyList());
            when(experienceHistoryRepository.findUserCategoryExpByPeriod(eq(userId3), any(), any()))
                .thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories())
                .thenReturn(Collections.emptyList());
            when(historyRepository.save(any(DailyMvpHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            dailyMvpHistoryService.reprocessDailyMvp(TEST_DATE);

            // then
            verify(historyRepository).deleteByMvpDate(TEST_DATE);
            verify(categoryStatsRepository).deleteByStatsDate(TEST_DATE);

            ArgumentCaptor<DailyMvpHistory> captor = ArgumentCaptor.forClass(DailyMvpHistory.class);
            verify(historyRepository, times(3)).save(captor.capture());

            List<DailyMvpHistory> saved = captor.getAllValues();
            assertThat(saved).hasSize(3);
            assertThat(saved.get(0).getMvpRank()).isEqualTo(1);
            assertThat(saved.get(0).getUserId()).isEqualTo(userId1);
            assertThat(saved.get(1).getMvpRank()).isEqualTo(2);
            assertThat(saved.get(1).getUserId()).isEqualTo(userId2);
            assertThat(saved.get(2).getMvpRank()).isEqualTo(3);
            assertThat(saved.get(2).getUserId()).isEqualTo(userId3);
        }
    }
}
