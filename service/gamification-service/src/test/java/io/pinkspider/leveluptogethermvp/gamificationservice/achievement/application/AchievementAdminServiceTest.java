package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AchievementCategory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.CheckLogicType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicComparisonOperator;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicDataSource;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.entity.Event;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.infrastructure.EventRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementCategoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.CheckLogicTypeRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserAchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AchievementAdminServiceTest {

    @Mock
    private AchievementRepository achievementRepository;

    @Mock
    private AchievementCategoryRepository achievementCategoryRepository;

    @Mock
    private CheckLogicTypeRepository checkLogicTypeRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserAchievementRepository userAchievementRepository;

    @Mock
    private UserTitleRepository userTitleRepository;

    @Mock
    private TitleRepository titleRepository;

    @InjectMocks
    private AchievementAdminService achievementAdminService;

    private AchievementCategory createTestCategory(Long id, String code, String name) {
        AchievementCategory category = AchievementCategory.builder()
            .code(code)
            .name(name)
            .description(name + " 설명")
            .sortOrder(1)
            .isActive(true)
            .build();
        setId(category, id);
        return category;
    }

    private CheckLogicType createTestCheckLogicType(Long id, String code) {
        CheckLogicType checkLogicType = CheckLogicType.builder()
            .code(code)
            .name(code + " 체크 로직")
            .dataSource(CheckLogicDataSource.USER_STATS)
            .dataField("totalMissionCompletions")
            .comparisonOperator(CheckLogicComparisonOperator.GTE)
            .sortOrder(1)
            .isActive(true)
            .build();
        setId(checkLogicType, id);
        return checkLogicType;
    }

    private Achievement createTestAchievement(Long id, String name, AchievementCategory category) {
        Achievement achievement = Achievement.builder()
            .name(name)
            .description(name + " 설명")
            .categoryCode(category != null ? category.getCode() : "MISSION")
            .requiredCount(10)
            .rewardExp(100)
            .isActive(true)
            .isHidden(false)
            .checkLogicDataSource("USER_STATS")
            .checkLogicDataField("totalMissionCompletions")
            .comparisonOperator("GTE")
            .build();
        if (category != null) {
            achievement.setCategory(category);
        }
        setId(achievement, id);
        return achievement;
    }

    private Title createTestTitle(Long id, String name) {
        Title title = Title.builder()
            .name(name)
            .description(name + " 설명")
            .rarity(TitleRarity.RARE)
            .positionType(TitlePosition.LEFT)
            .acquisitionType(TitleAcquisitionType.ACHIEVEMENT)
            .isActive(true)
            .build();
        setId(title, id);
        return title;
    }

    private AchievementAdminRequest createTestRequest(Long categoryId, Long checkLogicTypeId) {
        return AchievementAdminRequest.builder()
            .name("테스트 업적")
            .nameEn("Test Achievement")
            .description("테스트 업적 설명")
            .categoryId(categoryId)
            .checkLogicTypeId(checkLogicTypeId)
            .requiredCount(10)
            .rewardExp(100)
            .isHidden(false)
            .isActive(true)
            .build();
    }

    @Nested
    @DisplayName("getAllAchievements 테스트")
    class GetAllAchievementsTest {

        @Test
        @DisplayName("전체 업적 목록을 조회한다")
        void getAllAchievements_success() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            Achievement achievement1 = createTestAchievement(1L, "미션 달성 초급", category);
            Achievement achievement2 = createTestAchievement(2L, "미션 달성 중급", category);

            when(achievementRepository.findAll()).thenReturn(List.of(achievement1, achievement2));

            // when
            List<AchievementAdminResponse> result = achievementAdminService.getAllAchievements();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("미션 달성 초급");
            assertThat(result.get(1).getName()).isEqualTo("미션 달성 중급");
        }

        @Test
        @DisplayName("업적이 없으면 빈 목록을 반환한다")
        void getAllAchievements_empty() {
            // given
            when(achievementRepository.findAll()).thenReturn(List.of());

            // when
            List<AchievementAdminResponse> result = achievementAdminService.getAllAchievements();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchAchievements 테스트")
    class SearchAchievementsTest {

        @Test
        @DisplayName("키워드와 카테고리 ID로 업적을 검색한다")
        void searchAchievements_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            Achievement achievement = createTestAchievement(1L, "미션 달성", category);
            Page<Achievement> page = new PageImpl<>(List.of(achievement), pageable, 1);

            when(achievementRepository.searchByKeywordAndCategoryId("미션", 1L, pageable)).thenReturn(page);

            // when
            AchievementAdminPageResponse result = achievementAdminService.searchAchievements("미션", 1L, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getActiveAchievements 테스트")
    class GetActiveAchievementsTest {

        @Test
        @DisplayName("활성화된 업적 목록을 조회한다")
        void getActiveAchievements_success() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            Achievement achievement = createTestAchievement(1L, "미션 달성", category);

            when(achievementRepository.findByIsActiveTrueOrderByIdAsc()).thenReturn(List.of(achievement));

            // when
            List<AchievementAdminResponse> result = achievementAdminService.getActiveAchievements();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("getVisibleAchievements 테스트")
    class GetVisibleAchievementsTest {

        @Test
        @DisplayName("공개 업적 목록을 조회한다")
        void getVisibleAchievements_success() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            Achievement achievement = createTestAchievement(1L, "미션 달성", category);

            when(achievementRepository.findVisibleAchievementsOrderByIdAsc()).thenReturn(List.of(achievement));

            // when
            List<AchievementAdminResponse> result = achievementAdminService.getVisibleAchievements();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsHidden()).isFalse();
        }
    }

    @Nested
    @DisplayName("getAchievement 테스트")
    class GetAchievementTest {

        @Test
        @DisplayName("ID로 업적을 조회한다")
        void getAchievement_success() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            Achievement achievement = createTestAchievement(1L, "미션 달성", category);
            achievement.setCheckLogicTypeId(1L);
            CheckLogicType checkLogicType = createTestCheckLogicType(1L, "MISSION_COUNT");

            when(achievementRepository.findById(1L)).thenReturn(Optional.of(achievement));
            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(checkLogicType));

            // when
            AchievementAdminResponse result = achievementAdminService.getAchievement(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("미션 달성");
            assertThat(result.getCheckLogicTypeName()).isEqualTo("MISSION_COUNT 체크 로직");
        }

        @Test
        @DisplayName("존재하지 않는 업적을 조회하면 예외가 발생한다")
        void getAchievement_notFound() {
            // given
            when(achievementRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> achievementAdminService.getAchievement(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("업적을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("getAchievementsByCategoryCode 테스트")
    class GetAchievementsByCategoryCodeTest {

        @Test
        @DisplayName("카테고리 코드로 업적 목록을 조회한다")
        void getAchievementsByCategoryCode_success() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            Achievement achievement = createTestAchievement(1L, "미션 달성", category);

            when(achievementRepository.findByCategoryCode("MISSION")).thenReturn(List.of(achievement));

            // when
            List<AchievementAdminResponse> result = achievementAdminService.getAchievementsByCategoryCode("MISSION");

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategoryCode()).isEqualTo("MISSION");
        }
    }

    @Nested
    @DisplayName("createAchievement 테스트")
    class CreateAchievementTest {

        @Test
        @DisplayName("업적을 생성한다")
        void createAchievement_success() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            CheckLogicType checkLogicType = createTestCheckLogicType(1L, "MISSION_COUNT");
            AchievementAdminRequest request = createTestRequest(1L, 1L);

            // savedAchievement: rewardTitleId=null, checkLogicTypeId=null → toResponseWithEnrichment에서 findById 호출 없음
            Achievement savedAchievement = createTestAchievement(1L, "테스트 업적", category);

            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(checkLogicType));
            when(achievementRepository.save(any(Achievement.class))).thenReturn(savedAchievement);

            // when
            AchievementAdminResponse result = achievementAdminService.createAchievement(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("테스트 업적");
            verify(achievementRepository).save(any(Achievement.class));
        }

        @Test
        @DisplayName("이벤트 ID가 있으면 이벤트 이름을 설정한다")
        void createAchievement_withEventId() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            CheckLogicType checkLogicType = createTestCheckLogicType(1L, "MISSION_COUNT");

            Event event = Event.builder()
                .name("특별 이벤트")
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();
            setId(event, 10L);

            AchievementAdminRequest request = AchievementAdminRequest.builder()
                .name("이벤트 업적")
                .categoryId(1L)
                .checkLogicTypeId(1L)
                .requiredCount(5)
                .rewardExp(50)
                .eventId(10L)
                .isActive(true)
                .build();

            Achievement savedAchievement = createTestAchievement(1L, "이벤트 업적", category);
            savedAchievement.setEventId(10L);
            savedAchievement.setEventName("특별 이벤트");

            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(checkLogicType));
            when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
            when(achievementRepository.save(any(Achievement.class))).thenReturn(savedAchievement);

            // when
            AchievementAdminResponse result = achievementAdminService.createAchievement(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getEventName()).isEqualTo("특별 이벤트");
        }

        @Test
        @DisplayName("카테고리가 존재하지 않으면 예외가 발생한다")
        void createAchievement_categoryNotFound() {
            // given
            AchievementAdminRequest request = createTestRequest(999L, 1L);

            when(achievementCategoryRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> achievementAdminService.createAchievement(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("업적 카테고리를 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("체크 로직 유형이 존재하지 않으면 예외가 발생한다")
        void createAchievement_checkLogicTypeNotFound() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            AchievementAdminRequest request = createTestRequest(1L, 999L);

            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(checkLogicTypeRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> achievementAdminService.createAchievement(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("체크 로직 유형을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("updateAchievement 테스트")
    class UpdateAchievementTest {

        @Test
        @DisplayName("업적을 수정한다")
        void updateAchievement_success() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            CheckLogicType checkLogicType = createTestCheckLogicType(1L, "MISSION_COUNT");
            Achievement achievement = createTestAchievement(1L, "기존 업적", category);
            AchievementAdminRequest request = createTestRequest(1L, 1L);
            request.setName("수정된 업적");

            when(achievementRepository.findById(1L)).thenReturn(Optional.of(achievement));
            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(checkLogicType));
            when(achievementRepository.save(any(Achievement.class))).thenReturn(achievement);

            // when
            AchievementAdminResponse result = achievementAdminService.updateAchievement(1L, request);

            // then
            assertThat(result).isNotNull();
            verify(achievementRepository).save(any(Achievement.class));
        }

        @Test
        @DisplayName("칭호 보상이 변경되면 기존 달성자에게 칭호를 소급 부여한다")
        void updateAchievement_titleChanged_grantsToExistingAchievers() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            CheckLogicType checkLogicType = createTestCheckLogicType(1L, "MISSION_COUNT");
            Achievement achievement = createTestAchievement(1L, "기존 업적", category);
            achievement.setRewardTitleId(null); // 기존에는 칭호 없음

            Title newTitle = createTestTitle(5L, "새 칭호");

            AchievementAdminRequest request = createTestRequest(1L, 1L);
            request.setRewardTitleId(5L); // 새 칭호 설정

            UserAchievement existingAchiever = UserAchievement.builder()
                .userId("achiever-user")
                .achievement(achievement)
                .currentCount(10)
                .isCompleted(true)
                .isRewardClaimed(true)
                .build();
            setId(existingAchiever, 1L);

            when(achievementRepository.findById(1L)).thenReturn(Optional.of(achievement));
            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(checkLogicType));
            when(achievementRepository.save(any(Achievement.class))).thenReturn(achievement);
            when(titleRepository.findById(5L)).thenReturn(Optional.of(newTitle));
            when(userAchievementRepository.findByAchievementIdAndIsCompletedTrueAndIsRewardClaimedTrue(1L))
                .thenReturn(List.of(existingAchiever));
            when(userTitleRepository.existsByUserIdAndTitleId("achiever-user", 5L)).thenReturn(false);
            when(userTitleRepository.save(any(UserTitle.class))).thenReturn(null);

            // when
            achievementAdminService.updateAchievement(1L, request);

            // then
            verify(userTitleRepository).save(any(UserTitle.class));
        }

        @Test
        @DisplayName("존재하지 않는 업적을 수정하면 예외가 발생한다")
        void updateAchievement_notFound() {
            // given
            AchievementAdminRequest request = createTestRequest(1L, 1L);
            when(achievementRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> achievementAdminService.updateAchievement(999L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("업적을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("toggleActiveStatus 테스트")
    class ToggleActiveStatusTest {

        @Test
        @DisplayName("업적 활성화 상태를 토글한다 - 활성화 -> 비활성화")
        void toggleActiveStatus_activeToInactive() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            Achievement achievement = createTestAchievement(1L, "미션 달성", category);
            achievement.setIsActive(true);

            when(achievementRepository.findById(1L)).thenReturn(Optional.of(achievement));
            when(achievementRepository.save(any(Achievement.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            AchievementAdminResponse result = achievementAdminService.toggleActiveStatus(1L);

            // then
            assertThat(result.getIsActive()).isFalse();
            verify(achievementRepository).save(achievement);
        }

        @Test
        @DisplayName("업적 활성화 상태를 토글한다 - 비활성화 -> 활성화")
        void toggleActiveStatus_inactiveToActive() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            Achievement achievement = createTestAchievement(1L, "미션 달성", category);
            achievement.setIsActive(false);

            when(achievementRepository.findById(1L)).thenReturn(Optional.of(achievement));
            when(achievementRepository.save(any(Achievement.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            AchievementAdminResponse result = achievementAdminService.toggleActiveStatus(1L);

            // then
            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 업적의 상태를 토글하면 예외가 발생한다")
        void toggleActiveStatus_notFound() {
            // given
            when(achievementRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> achievementAdminService.toggleActiveStatus(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("업적을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("deleteAchievement 테스트")
    class DeleteAchievementTest {

        @Test
        @DisplayName("업적을 삭제한다")
        void deleteAchievement_success() {
            // given
            when(achievementRepository.existsById(1L)).thenReturn(true);

            // when
            achievementAdminService.deleteAchievement(1L);

            // then
            verify(achievementRepository).deleteById(1L);
        }

        @Test
        @DisplayName("존재하지 않는 업적을 삭제하면 예외가 발생한다")
        void deleteAchievement_notFound() {
            // given
            when(achievementRepository.existsById(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> achievementAdminService.deleteAchievement(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("업적을 찾을 수 없습니다.");

            verify(achievementRepository, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("보상 칭호 소급 부여 테스트")
    class GrantTitleToExistingAchieversTest {

        @Test
        @DisplayName("이미 칭호를 보유한 사용자에게는 소급 부여하지 않는다")
        void updateAchievement_alreadyHasTitle_notGranted() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            CheckLogicType checkLogicType = createTestCheckLogicType(1L, "MISSION_COUNT");
            Achievement achievement = createTestAchievement(1L, "기존 업적", category);
            achievement.setRewardTitleId(null);

            Title newTitle = createTestTitle(5L, "새 칭호");

            AchievementAdminRequest request = createTestRequest(1L, 1L);
            request.setRewardTitleId(5L);

            UserAchievement existingAchiever = UserAchievement.builder()
                .userId("achiever-user")
                .achievement(achievement)
                .currentCount(10)
                .isCompleted(true)
                .isRewardClaimed(true)
                .build();
            setId(existingAchiever, 1L);

            when(achievementRepository.findById(1L)).thenReturn(Optional.of(achievement));
            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(checkLogicType));
            when(achievementRepository.save(any(Achievement.class))).thenReturn(achievement);
            when(titleRepository.findById(5L)).thenReturn(Optional.of(newTitle));
            when(userAchievementRepository.findByAchievementIdAndIsCompletedTrueAndIsRewardClaimedTrue(1L))
                .thenReturn(List.of(existingAchiever));
            when(userTitleRepository.existsByUserIdAndTitleId("achiever-user", 5L)).thenReturn(true);

            // when
            achievementAdminService.updateAchievement(1L, request);

            // then
            verify(userTitleRepository, never()).save(any(UserTitle.class));
        }

        @Test
        @DisplayName("소급 부여할 칭호가 없으면 건너뛴다")
        void updateAchievement_titleNotFound_skipsGrant() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            CheckLogicType checkLogicType = createTestCheckLogicType(1L, "MISSION_COUNT");
            Achievement achievement = createTestAchievement(1L, "기존 업적", category);
            achievement.setRewardTitleId(null);

            AchievementAdminRequest request = createTestRequest(1L, 1L);
            request.setRewardTitleId(99L); // 존재하지 않는 칭호 ID

            when(achievementRepository.findById(1L)).thenReturn(Optional.of(achievement));
            when(achievementCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(checkLogicTypeRepository.findById(1L)).thenReturn(Optional.of(checkLogicType));
            when(achievementRepository.save(any(Achievement.class))).thenReturn(achievement);
            when(titleRepository.findById(99L)).thenReturn(Optional.empty()); // 칭호 없음

            // when
            achievementAdminService.updateAchievement(1L, request);

            // then
            verify(userAchievementRepository, never()).findByAchievementIdAndIsCompletedTrueAndIsRewardClaimedTrue(anyLong());
            verify(userTitleRepository, never()).save(any(UserTitle.class));
        }
    }

    @Nested
    @DisplayName("보상 칭호 이름 조회 테스트 (toResponseWithEnrichment)")
    class ToResponseWithEnrichmentTest {

        @Test
        @DisplayName("보상 칭호 이름을 함께 조회한다")
        void getAchievement_withRewardTitle() {
            // given
            AchievementCategory category = createTestCategory(1L, "MISSION", "미션");
            Achievement achievement = createTestAchievement(1L, "미션 달성", category);
            achievement.setRewardTitleId(5L);

            Title title = createTestTitle(5L, "달성자 칭호");

            when(achievementRepository.findById(1L)).thenReturn(Optional.of(achievement));
            when(titleRepository.findById(5L)).thenReturn(Optional.of(title));

            // when
            AchievementAdminResponse result = achievementAdminService.getAchievement(1L);

            // then
            assertThat(result.getRewardTitleName()).isEqualTo("달성자 칭호");
        }
    }
}
