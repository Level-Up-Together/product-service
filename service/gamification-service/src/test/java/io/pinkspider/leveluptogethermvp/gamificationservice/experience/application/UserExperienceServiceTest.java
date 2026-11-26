package io.pinkspider.leveluptogethermvp.gamificationservice.experience.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.global.event.UserLevelUpEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.event.AchievementCheckRequestedEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.ExperienceHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserCategoryExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto.UserExperienceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserCategoryExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.application.UserLevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class UserExperienceServiceTest {

    @Mock private UserExperienceRepository userExperienceRepository;

    @Mock private ExperienceHistoryRepository experienceHistoryRepository;

    @Mock private UserCategoryExperienceRepository userCategoryExperienceRepository;

    @Mock private UserLevelConfigCacheService userLevelConfigCacheService;

    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock
    private io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondService
            diamondService;

    @InjectMocks private UserExperienceService userExperienceService;

    private static final String TEST_USER_ID = "test-user-123";

    private UserExperience createTestUserExperience(
            Long id, String userId, int level, int currentExp, int totalExp) {
        UserExperience userExp =
                UserExperience.builder()
                        .userId(userId)
                        .currentLevel(level)
                        .currentExp(currentExp)
                        .totalExp(totalExp)
                        .build();
        setId(userExp, id);
        return userExp;
    }

    private UserLevelConfig createUserLevelConfig(
            int level, int requiredExp, Integer cumulativeExp) {
        return UserLevelConfig.builder()
                .level(level)
                .requiredExp(requiredExp)
                .cumulativeExp(cumulativeExp)
                .build();
    }

    @Nested
    @DisplayName("addExperience 테스트")
    class AddExperienceTest {

        @Test
        @DisplayName("경험치를 정상적으로 추가한다")
        void addExperience_success() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 50, 50);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 30, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료");

            // then
            assertThat(result).isNotNull();
            assertThat(userExp.getCurrentExp()).isEqualTo(80);
            assertThat(userExp.getTotalExp()).isEqualTo(80);
            verify(experienceHistoryRepository).save(any(ExperienceHistory.class));
        }

        @Test
        @DisplayName("경험치가 없는 사용자에게 새 경험치 기록을 생성한다")
        void addExperience_newUser_createsRecord() {
            // given
            UserExperience newUserExp = createTestUserExperience(1L, TEST_USER_ID, 1, 0, 0);

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userExperienceRepository.save(any(UserExperience.class))).thenReturn(newUserExp);
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 50, ExpSourceType.MISSION_EXECUTION, 1L, "첫 미션 완료");

            // then
            assertThat(result).isNotNull();
            verify(userExperienceRepository).save(any(UserExperience.class));
        }

        @Test
        @DisplayName("레벨업 조건을 충족하면 레벨이 증가한다 (기본 공식)")
        void addExperience_levelUp_defaultFormula() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 90, 90);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 20, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료");

            // then
            assertThat(result).isNotNull();
            assertThat(userExp.getCurrentLevel()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getUserExperience 테스트")
    class GetUserExperienceTest {

        @Test
        @DisplayName("사용자 경험치 정보를 조회한다")
        void getUserExperience_success() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 5, 250, 750);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));

            // when
            UserExperienceResponse result = userExperienceService.getUserExperience(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCurrentLevel()).isEqualTo(5);
            assertThat(result.getCurrentExp()).isEqualTo(250);
        }

        @Test
        @DisplayName("경험치가 없는 사용자는 새 기록을 생성하여 반환한다")
        void getUserExperience_newUser() {
            // given
            UserExperience newUserExp = createTestUserExperience(1L, TEST_USER_ID, 1, 0, 0);

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userExperienceRepository.save(any(UserExperience.class))).thenReturn(newUserExp);

            // when
            UserExperienceResponse result = userExperienceService.getUserExperience(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCurrentLevel()).isEqualTo(1);
            assertThat(result.getCurrentExp()).isEqualTo(0);
            assertThat(result.getTotalExp()).isEqualTo(0);
            verify(userExperienceRepository).save(any(UserExperience.class));
        }

        @Test
        @DisplayName("신규 사용자 생성 시 레벨 1, 경험치 0으로 초기화된다")
        void getUserExperience_newUser_initialValues() {
            // given
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userExperienceRepository.save(any(UserExperience.class)))
                    .thenAnswer(
                            invocation -> {
                                UserExperience saved = invocation.getArgument(0);
                                setId(saved, 1L);
                                return saved;
                            });

            // when
            UserExperienceResponse result = userExperienceService.getUserExperience(TEST_USER_ID);

            // then
            assertThat(result.getCurrentLevel()).isEqualTo(1);
            assertThat(result.getCurrentExp()).isEqualTo(0);
            assertThat(result.getTotalExp()).isEqualTo(0);
            assertThat(result.getNextLevelRequiredExp()).isEqualTo(100);
        }

        @Test
        @DisplayName("UserLevelConfig가 없어도 기본 공식으로 다음 레벨 경험치를 계산한다")
        void getUserExperience_noUserLevelConfig_usesDefaultFormula() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 3, 50, 300);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));

            // when
            UserExperienceResponse result = userExperienceService.getUserExperience(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCurrentLevel()).isEqualTo(3);
            // 기본 공식: 100 + (level - 1) * 50 = 100 + 2 * 50 = 200
            assertThat(result.getNextLevelRequiredExp()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("다음 레벨 필요 경험치 (LUT-570)")
    class NextLevelRequiredExpTest {

        private final List<UserLevelConfig> earlyCurve =
                List.of(
                        createUserLevelConfig(1, 0, 0),
                        createUserLevelConfig(2, 50, 50),
                        createUserLevelConfig(3, 150, 200),
                        createUserLevelConfig(4, 200, 400));

        @Test
        @DisplayName("레벨 2 유저의 next_level_required_exp 는 레벨 3 행(150)이다 — 현재 레벨 행(50)이 아니다")
        void usesNextLevelRow() {
            // given: 레벨 2, 현재 레벨 안에서 30 쌓임
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 2, 30, 80);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs()).thenReturn(earlyCurve);

            // when
            UserExperienceResponse result = userExperienceService.getUserExperience(TEST_USER_ID);

            // then
            assertThat(result.getNextLevelRequiredExp()).isEqualTo(150);
            assertThat(result.getExpToNextLevel()).isEqualTo(120);
            assertThat(result.getProgressToNextLevel()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("레벨업 판정과 같은 행을 본다 — 레벨 1 에서 50 을 모으면 레벨 2 가 되고 다음 필요치는 150")
        void consistentWithLevelUp() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 40, 40);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs()).thenReturn(earlyCurve);
            when(userLevelConfigCacheService.getMaxLevel()).thenReturn(4);

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 10, ExpSourceType.MISSION_EXECUTION, 1L, "체크 완료");

            // then
            assertThat(result.getCurrentLevel()).isEqualTo(2);
            assertThat(result.getCurrentExp()).isEqualTo(0);
            assertThat(result.getNextLevelRequiredExp()).isEqualTo(150);
        }

        @Test
        @DisplayName("최대 레벨이면 다음 레벨 행이 없어 null, 남은 경험치 0·진행률 0")
        void maxLevel_returnsNull() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 4, 10, 410);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs()).thenReturn(earlyCurve);

            // when
            UserExperienceResponse result = userExperienceService.getUserExperience(TEST_USER_ID);

            // then
            assertThat(result.getNextLevelRequiredExp()).isNull();
            assertThat(result.getExpToNextLevel()).isEqualTo(0);
            assertThat(result.getProgressToNextLevel()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("레벨 테이블이 비어 있으면 기본 공식(100 + (L-1)×50)을 쓴다")
        void emptyTable_usesDefaultFormula() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 3, 50, 300);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse result = userExperienceService.getUserExperience(TEST_USER_ID);

            // then
            assertThat(result.getNextLevelRequiredExp()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("getOrCreateUserExperience 테스트")
    class GetOrCreateUserExperienceTest {

        @Test
        @DisplayName("기존 경험치가 있으면 그대로 반환한다")
        void getOrCreateUserExperience_existingUser() {
            // given
            UserExperience existingExp = createTestUserExperience(1L, TEST_USER_ID, 5, 100, 500);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(existingExp));

            // when
            UserExperience result = userExperienceService.getOrCreateUserExperience(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(existingExp);
            verify(userExperienceRepository, never()).save(any(UserExperience.class));
        }

        @Test
        @DisplayName("경험치가 없으면 새로 생성한다")
        void getOrCreateUserExperience_newUser() {
            // given
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userExperienceRepository.save(any(UserExperience.class)))
                    .thenAnswer(
                            invocation -> {
                                UserExperience saved = invocation.getArgument(0);
                                setId(saved, 1L);
                                return saved;
                            });

            // when
            UserExperience result = userExperienceService.getOrCreateUserExperience(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(result.getCurrentLevel()).isEqualTo(1);
            assertThat(result.getCurrentExp()).isEqualTo(0);
            assertThat(result.getTotalExp()).isEqualTo(0);
            verify(userExperienceRepository).save(any(UserExperience.class));
        }
    }

    @Nested
    @DisplayName("getExperienceHistory 테스트")
    class GetExperienceHistoryTest {

        @Test
        @DisplayName("경험치 히스토리를 페이지로 조회한다")
        void getExperienceHistory_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            ExperienceHistory history1 =
                    ExperienceHistory.builder()
                            .userId(TEST_USER_ID)
                            .sourceType(ExpSourceType.MISSION_EXECUTION)
                            .expAmount(50)
                            .description("미션 완료")
                            .build();
            ExperienceHistory history2 =
                    ExperienceHistory.builder()
                            .userId(TEST_USER_ID)
                            .sourceType(ExpSourceType.EVENT)
                            .expAmount(10)
                            .description("출석 체크")
                            .build();
            Page<ExperienceHistory> page = new PageImpl<>(List.of(history1, history2), pageable, 2);

            when(experienceHistoryRepository.findByUserIdOrderByCreatedAtDesc(
                            TEST_USER_ID, pageable))
                    .thenReturn(page);

            // when
            Page<ExperienceHistory> result =
                    userExperienceService.getExperienceHistory(TEST_USER_ID, pageable);

            // then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getExpAmount()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("subtractExperience 테스트")
    class SubtractExperienceTest {

        @Test
        @DisplayName("경험치를 정상적으로 차감한다")
        void subtractExperience_success() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 3, 150, 350);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));

            // when
            UserExperienceResponse result =
                    userExperienceService.subtractExperience(
                            TEST_USER_ID, 50, ExpSourceType.MISSION_EXECUTION, 1L, "보상 취소");

            // then
            assertThat(result).isNotNull();
            verify(experienceHistoryRepository).save(any(ExperienceHistory.class));
        }

        @Test
        @DisplayName("경험치 차감 시 레벨이 낮아질 수 있다")
        void subtractExperience_levelDown() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 3, 10, 260);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(
                            List.of(
                                    createUserLevelConfig(1, 100, 0),
                                    createUserLevelConfig(2, 150, 100),
                                    createUserLevelConfig(3, 200, 250)));

            // when
            UserExperienceResponse result =
                    userExperienceService.subtractExperience(
                            TEST_USER_ID, 100, ExpSourceType.MISSION_EXECUTION, 1L, "보상 취소");

            // then
            assertThat(result).isNotNull();
            // 경험치가 음수가 되면 레벨 다운 처리됨
        }

        @Test
        @DisplayName("경험치가 0 이하가 되면 레벨 1로 리셋된다")
        void subtractExperience_resetToLevel1() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 2, 50, 150);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(
                            List.of(
                                    createUserLevelConfig(1, 100, 0),
                                    createUserLevelConfig(2, 150, 100)));

            // when
            UserExperienceResponse result =
                    userExperienceService.subtractExperience(
                            TEST_USER_ID, 200, ExpSourceType.MISSION_EXECUTION, 1L, "대량 차감");

            // then
            assertThat(result).isNotNull();
            assertThat(userExp.getCurrentLevel()).isEqualTo(1);
            assertThat(userExp.getTotalExp()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getAllLevelConfigs 테스트")
    class GetAllLevelConfigsTest {

        @Test
        @DisplayName("모든 레벨 설정을 조회한다")
        void getAllLevelConfigs_success() {
            // given
            List<UserLevelConfig> configs =
                    List.of(
                            createUserLevelConfig(1, 100, 0),
                            createUserLevelConfig(2, 150, 100),
                            createUserLevelConfig(3, 200, 250));

            when(userLevelConfigCacheService.getAllLevelConfigs()).thenReturn(configs);

            // when
            List<UserLevelConfig> result = userExperienceService.getAllLevelConfigs();

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getLevel()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("createOrUpdateLevelConfig 테스트")
    class CreateOrUpdateLevelConfigTest {

        @Test
        @DisplayName("새 레벨 설정을 생성한다")
        void createOrUpdateLevelConfig_create() {
            // given
            UserLevelConfig newConfig = createUserLevelConfig(10, 500, 2000);

            when(userLevelConfigCacheService.createOrUpdateLevelConfig(10, 500, 2000))
                    .thenReturn(newConfig);

            // when
            UserLevelConfig result = userExperienceService.createOrUpdateLevelConfig(10, 500, 2000);

            // then
            assertThat(result).isNotNull();
            verify(userLevelConfigCacheService).createOrUpdateLevelConfig(10, 500, 2000);
        }

        @Test
        @DisplayName("기존 레벨 설정을 업데이트한다")
        void createOrUpdateLevelConfig_update() {
            // given
            UserLevelConfig updatedConfig = createUserLevelConfig(5, 350, 750);

            when(userLevelConfigCacheService.createOrUpdateLevelConfig(5, 350, 750))
                    .thenReturn(updatedConfig);

            // when
            UserLevelConfig result = userExperienceService.createOrUpdateLevelConfig(5, 350, 750);

            // then
            assertThat(result.getRequiredExp()).isEqualTo(350);
            verify(userLevelConfigCacheService).createOrUpdateLevelConfig(5, 350, 750);
        }
    }

    @Nested
    @DisplayName("addExperience 오버로드 테스트")
    class AddExperienceOverloadTest {

        @Test
        @DisplayName("카테고리명만 포함하여 경험치를 추가한다 (6파라미터)")
        void addExperience_withCategoryName() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 50, 50);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 30, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료", "건강");

            // then
            assertThat(result).isNotNull();
            assertThat(userExp.getCurrentExp()).isEqualTo(80);
            verify(experienceHistoryRepository).save(any(ExperienceHistory.class));
            // categoryId가 null이므로 카테고리 경험치는 업데이트되지 않음
            verify(userCategoryExperienceRepository, never())
                    .save(any(UserCategoryExperience.class));
        }

        @Test
        @DisplayName("카테고리ID와 카테고리명을 포함하여 경험치를 추가하면 카테고리 경험치도 업데이트된다")
        void addExperience_withCategoryIdAndName_updatesCategoryExp() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 50, 50);
            UserCategoryExperience categoryExp =
                    UserCategoryExperience.create(TEST_USER_ID, 1L, "건강", 100);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                    .thenReturn(Optional.of(categoryExp));

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID,
                            30,
                            ExpSourceType.MISSION_EXECUTION,
                            1L,
                            "미션 완료",
                            1L,
                            "건강");

            // then
            assertThat(result).isNotNull();
            assertThat(userExp.getCurrentExp()).isEqualTo(80);
            verify(userCategoryExperienceRepository).save(categoryExp);
            assertThat(categoryExp.getTotalExp()).isEqualTo(130); // 100 + 30
        }

        @Test
        @DisplayName("카테고리 경험치가 없으면 새로 생성한다")
        void addExperience_withCategoryId_createsNewCategoryExp() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 50, 50);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                    .thenReturn(Optional.empty());

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID,
                            30,
                            ExpSourceType.MISSION_EXECUTION,
                            1L,
                            "미션 완료",
                            1L,
                            "건강");

            // then
            assertThat(result).isNotNull();
            verify(userCategoryExperienceRepository).save(any(UserCategoryExperience.class));
        }

        @Test
        @DisplayName("카테고리 없는 경험치 지급 시 USER_EXPERIENCE 업적 체크 이벤트를 발행한다")
        void addExperience_withoutCategory_publishesUserExperienceCheckEvent() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 50, 50);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            userExperienceService.addExperience(
                    TEST_USER_ID, 30, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료");

            // then: 커밋 후 업적 체크용 이벤트 발행 (카테고리 없음 → USER_EXPERIENCE 만)
            verify(eventPublisher)
                    .publishEvent(
                            new AchievementCheckRequestedEvent(
                                    TEST_USER_ID, List.of("USER_EXPERIENCE")));
        }

        @Test
        @DisplayName("카테고리 경험치 지급 시 USER_CATEGORY_EXPERIENCE 업적 체크 이벤트도 발행한다")
        void addExperience_withCategory_publishesCategoryCheckEvent() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 50, 50);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                    .thenReturn(Optional.empty());

            // when
            userExperienceService.addExperience(
                    TEST_USER_ID, 30, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료", 1L, "건강");

            // then
            verify(eventPublisher)
                    .publishEvent(
                            new AchievementCheckRequestedEvent(
                                    TEST_USER_ID,
                                    List.of("USER_EXPERIENCE", "USER_CATEGORY_EXPERIENCE")));
        }

        @Test
        @DisplayName("레벨업 시 UserLevelUpEvent 와 업적 체크 이벤트를 모두 발행한다")
        void addExperience_levelUp_publishesBothEvents() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 90, 90);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            userExperienceService.addExperience(
                    TEST_USER_ID, 20, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료");

            // then
            verify(eventPublisher).publishEvent(any(UserLevelUpEvent.class));
            verify(eventPublisher)
                    .publishEvent(
                            new AchievementCheckRequestedEvent(
                                    TEST_USER_ID, List.of("USER_EXPERIENCE")));
        }
    }

    @Nested
    @DisplayName("subtractExperience 오버로드 테스트")
    class SubtractExperienceOverloadTest {

        @Test
        @DisplayName("카테고리명만 포함하여 경험치를 차감한다 (6파라미터)")
        void subtractExperience_withCategoryName() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 3, 150, 350);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));

            // when
            UserExperienceResponse result =
                    userExperienceService.subtractExperience(
                            TEST_USER_ID, 50, ExpSourceType.MISSION_EXECUTION, 1L, "보상 취소", "건강");

            // then
            assertThat(result).isNotNull();
            verify(experienceHistoryRepository).save(any(ExperienceHistory.class));
            // categoryId가 null이므로 카테고리 경험치는 차감되지 않음
            verify(userCategoryExperienceRepository, never())
                    .findByUserIdAndCategoryId(any(), any());
        }

        @Test
        @DisplayName("카테고리ID를 포함하여 경험치를 차감하면 카테고리 경험치도 차감된다")
        void subtractExperience_withCategoryId_subtractsCategoryExp() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 3, 150, 350);
            UserCategoryExperience categoryExp =
                    UserCategoryExperience.create(TEST_USER_ID, 1L, "건강", 100);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                    .thenReturn(Optional.of(categoryExp));

            // when
            UserExperienceResponse result =
                    userExperienceService.subtractExperience(
                            TEST_USER_ID,
                            50,
                            ExpSourceType.MISSION_EXECUTION,
                            1L,
                            "보상 취소",
                            1L,
                            "건강");

            // then
            assertThat(result).isNotNull();
            verify(userCategoryExperienceRepository).save(categoryExp);
            assertThat(categoryExp.getTotalExp()).isEqualTo(50); // 100 - 50
        }

        @Test
        @DisplayName("카테고리 경험치가 0 미만이 되지 않도록 한다")
        void subtractExperience_withCategoryId_minZero() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 3, 150, 350);
            UserCategoryExperience categoryExp =
                    UserCategoryExperience.create(TEST_USER_ID, 1L, "건강", 30);

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                    .thenReturn(Optional.of(categoryExp));

            // when
            UserExperienceResponse result =
                    userExperienceService.subtractExperience(
                            TEST_USER_ID,
                            50,
                            ExpSourceType.MISSION_EXECUTION,
                            1L,
                            "보상 취소",
                            1L,
                            "건강");

            // then
            assertThat(result).isNotNull();
            assertThat(categoryExp.getTotalExp()).isEqualTo(0); // max(0, 30-50) = 0
        }
    }

    @Nested
    @DisplayName("processLevelUp 테스트 (UserLevelConfig 기반)")
    class ProcessLevelUpWithConfigTest {

        @Test
        @DisplayName("UserLevelConfig가 있을 때 레벨업한다")
        void addExperience_levelUpWithConfig() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 90, 90);
            // 새 로직: 다음 레벨의 required_exp를 체크하므로, level 2의 required_exp가 100이어야 110 exp로 레벨업 가능
            List<UserLevelConfig> configs =
                    List.of(
                            createUserLevelConfig(1, 0, 0), // level 1 (시작 레벨)
                            createUserLevelConfig(2, 100, 100), // level 2 도달에 100 exp 필요
                            createUserLevelConfig(3, 150, 250) // level 3 도달에 150 exp 추가 필요
                            );

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs()).thenReturn(configs);
            when(userLevelConfigCacheService.getMaxLevel()).thenReturn(3);

            // when - 20 exp 추가하면 총 110 exp, level 2의 required_exp(100)을 충족하므로 레벨업
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 20, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료");

            // then
            assertThat(result).isNotNull();
            assertThat(userExp.getCurrentLevel()).isEqualTo(2);
            assertThat(userExp.getCurrentExp()).isEqualTo(10); // 110 - 100 = 10
        }

        @Test
        @DisplayName("현재 레벨의 config가 없으면 레벨업하지 않는다")
        void addExperience_noConfigForCurrentLevel_noLevelUp() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 4, 50, 300);
            // 레벨 4의 config가 없음 (레벨 1-3만 있음)
            List<UserLevelConfig> configs =
                    List.of(
                            createUserLevelConfig(1, 100, 0),
                            createUserLevelConfig(2, 150, 100),
                            createUserLevelConfig(3, 200, 250));

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs()).thenReturn(configs);

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 100, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료");

            // then
            assertThat(result).isNotNull();
            assertThat(userExp.getCurrentLevel()).isEqualTo(4); // 레벨 유지 (config 없음)
            assertThat(userExp.getCurrentExp()).isEqualTo(150); // 50 + 100
        }

        @Test
        @DisplayName("연속 레벨업 (한 번에 여러 레벨 상승)")
        void addExperience_multipleLevelUp() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 0, 0);
            // 새 로직: 다음 레벨의 required_exp를 체크
            // level 2의 required_exp=100, level 3의 required_exp=150, level 4의 required_exp=200
            // 300 exp 추가 시: 300 >= 100 (level up to 2, remaining=200)
            //                  200 >= 150 (level up to 3, remaining=50)
            //                  50 < 200 (stop)
            List<UserLevelConfig> configs =
                    List.of(
                            createUserLevelConfig(1, 0, 0), // level 1 (시작 레벨)
                            createUserLevelConfig(2, 100, 100), // level 2 도달에 100 exp 필요
                            createUserLevelConfig(3, 150, 250), // level 3 도달에 150 exp 추가 필요
                            createUserLevelConfig(4, 200, 450) // level 4 도달에 200 exp 추가 필요
                            );

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs()).thenReturn(configs);
            when(userLevelConfigCacheService.getMaxLevel()).thenReturn(10);

            // when - 300 경험치 추가 (레벨 1 -> 2 -> 3까지 도달)
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 300, ExpSourceType.EVENT, 1L, "대량 경험치 획득");

            // then
            assertThat(result).isNotNull();
            assertThat(userExp.getCurrentLevel()).isEqualTo(3);
            assertThat(userExp.getCurrentExp()).isEqualTo(50); // 300 - 100 - 150 = 50
        }
    }

    @Nested
    @DisplayName("processLevelDown 테스트 (추가)")
    class ProcessLevelDownExtraTest {

        @Test
        @DisplayName("레벨 다운 시 누적 경험치가 없는 UserLevelConfig도 처리한다")
        void subtractExperience_levelDownWithNullCumulativeExp() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 3, 10, 260);
            UserLevelConfig config1 =
                    UserLevelConfig.builder()
                            .level(1)
                            .requiredExp(100)
                            .cumulativeExp(null) // 누적 경험치 없음
                            .build();
            UserLevelConfig config2 =
                    UserLevelConfig.builder()
                            .level(2)
                            .requiredExp(150)
                            .cumulativeExp(null) // 누적 경험치 없음
                            .build();

            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(List.of(config1, config2));

            // when
            UserExperienceResponse result =
                    userExperienceService.subtractExperience(
                            TEST_USER_ID, 100, ExpSourceType.MISSION_EXECUTION, 1L, "보상 취소");

            // then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getDailyExpSummary 테스트 (QA-217)")
    class GetDailyExpSummaryTest {

        @Test
        @DisplayName("일별 경험치 합계를 날짜 맵으로 변환한다")
        void getDailyExpSummary_success() {
            // given
            LocalDateTime startUtc = LocalDateTime.of(2026, 6, 30, 15, 0);
            LocalDateTime endUtc = LocalDateTime.of(2026, 7, 31, 15, 0);
            when(experienceHistoryRepository.sumDailyExpByUserIdAndPeriod(
                            TEST_USER_ID, startUtc, endUtc, "Asia/Seoul"))
                    .thenReturn(
                            List.of(
                                    new Object[] {
                                        java.sql.Date.valueOf("2026-07-01"),
                                        java.math.BigDecimal.valueOf(135)
                                    },
                                    new Object[] {
                                        java.sql.Date.valueOf("2026-07-02"),
                                        java.math.BigDecimal.valueOf(10)
                                    }));

            // when
            Map<LocalDate, Long> result =
                    userExperienceService.getDailyExpSummary(
                            TEST_USER_ID, startUtc, endUtc, "Asia/Seoul");

            // then
            assertThat(result)
                    .containsEntry(LocalDate.of(2026, 7, 1), 135L)
                    .containsEntry(LocalDate.of(2026, 7, 2), 10L);
        }

        @Test
        @DisplayName("유효하지 않은 타임존이면 Asia/Seoul 로 대체한다")
        void getDailyExpSummary_invalidTimezone_fallsBackToSeoul() {
            // given
            LocalDateTime startUtc = LocalDateTime.of(2026, 6, 30, 15, 0);
            LocalDateTime endUtc = LocalDateTime.of(2026, 7, 31, 15, 0);
            when(experienceHistoryRepository.sumDailyExpByUserIdAndPeriod(
                            TEST_USER_ID, startUtc, endUtc, "Asia/Seoul"))
                    .thenReturn(List.of());

            // when
            Map<LocalDate, Long> result =
                    userExperienceService.getDailyExpSummary(
                            TEST_USER_ID, startUtc, endUtc, "Invalid/Zone");

            // then
            assertThat(result).isEmpty();
            verify(experienceHistoryRepository)
                    .sumDailyExpByUserIdAndPeriod(TEST_USER_ID, startUtc, endUtc, "Asia/Seoul");
        }
    }

    @Nested
    @DisplayName("getDailyRewardSummary 테스트 (LUT-568)")
    class GetDailyRewardSummaryTest {

        @Test
        @DisplayName("출석·마일스톤 보상 행을 날짜/출처/출처ID/경험치 DTO 로 변환한다")
        void getDailyRewardSummary_success() {
            // given
            LocalDateTime startUtc = LocalDateTime.of(2026, 9, 30, 15, 0);
            LocalDateTime endUtc = LocalDateTime.of(2026, 10, 31, 15, 0);
            when(experienceHistoryRepository.findDailyRewardsByUserIdAndPeriod(
                            TEST_USER_ID, startUtc, endUtc, "Asia/Seoul"))
                    .thenReturn(
                            List.of(
                                    new Object[] {
                                        java.sql.Date.valueOf("2026-10-15"),
                                        "EVENT",
                                        77L,
                                        java.math.BigDecimal.valueOf(10)
                                    },
                                    new Object[] {
                                        java.sql.Date.valueOf("2026-10-15"),
                                        "DAILY_MISSION_MILESTONE",
                                        3L,
                                        java.math.BigDecimal.valueOf(20)
                                    },
                                    new Object[] {
                                        java.sql.Date.valueOf("2026-10-16"),
                                        "DAILY_MISSION_MILESTONE",
                                        null,
                                        java.math.BigDecimal.valueOf(20)
                                    }));

            // when
            List<io.pinkspider.global.facade.dto.DailyRewardDto> result =
                    userExperienceService.getDailyRewardSummary(
                            TEST_USER_ID, startUtc, endUtc, "Asia/Seoul");

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 10, 15));
            assertThat(result.get(0).sourceType()).isEqualTo(ExpSourceType.EVENT);
            assertThat(result.get(0).sourceId()).isEqualTo(77L);
            assertThat(result.get(0).exp()).isEqualTo(10L);
            assertThat(result.get(1).sourceType()).isEqualTo(ExpSourceType.DAILY_MISSION_MILESTONE);
            assertThat(result.get(1).sourceId()).isEqualTo(3L);
            assertThat(result.get(2).sourceId()).isNull();
        }

        @Test
        @DisplayName("유효하지 않은 타임존이면 Asia/Seoul 로 대체한다")
        void getDailyRewardSummary_invalidTimezone_fallsBackToSeoul() {
            // given
            LocalDateTime startUtc = LocalDateTime.of(2026, 9, 30, 15, 0);
            LocalDateTime endUtc = LocalDateTime.of(2026, 10, 31, 15, 0);
            when(experienceHistoryRepository.findDailyRewardsByUserIdAndPeriod(
                            TEST_USER_ID, startUtc, endUtc, "Asia/Seoul"))
                    .thenReturn(List.of());

            // when
            List<io.pinkspider.global.facade.dto.DailyRewardDto> result =
                    userExperienceService.getDailyRewardSummary(
                            TEST_USER_ID, startUtc, endUtc, "Invalid/Zone");

            // then
            assertThat(result).isEmpty();
            verify(experienceHistoryRepository)
                    .findDailyRewardsByUserIdAndPeriod(
                            TEST_USER_ID, startUtc, endUtc, "Asia/Seoul");
        }

        @Test
        @DisplayName("유저 타임존이 유효하면 그대로 넘긴다")
        void getDailyRewardSummary_validTimezone_passedThrough() {
            // given
            LocalDateTime startUtc = LocalDateTime.of(2026, 9, 30, 21, 0);
            LocalDateTime endUtc = LocalDateTime.of(2026, 10, 31, 21, 0);
            when(experienceHistoryRepository.findDailyRewardsByUserIdAndPeriod(
                            TEST_USER_ID, startUtc, endUtc, "Asia/Riyadh"))
                    .thenReturn(List.of());

            // when
            userExperienceService.getDailyRewardSummary(
                    TEST_USER_ID, startUtc, endUtc, "Asia/Riyadh");

            // then
            verify(experienceHistoryRepository)
                    .findDailyRewardsByUserIdAndPeriod(
                            TEST_USER_ID, startUtc, endUtc, "Asia/Riyadh");
        }
    }

    // ===== LUT-860 완료한 미션 수행 취소 =====

    private UserExperience experienceWithDebt(int level, int currentExp, int totalExp, int debt) {
        UserExperience userExp =
                createTestUserExperience(1L, TEST_USER_ID, level, currentExp, totalExp);
        userExp.setExpDebt(debt);
        return userExp;
    }

    private ExperienceHistory savedHistory() {
        org.mockito.ArgumentCaptor<ExperienceHistory> captor =
                org.mockito.ArgumentCaptor.forClass(ExperienceHistory.class);
        verify(experienceHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("LUT-860 addExperience — 미션 경험치는 누적 마이너스 경험치를 먼저 갚는다")
    class AddExperiencePaysDebtTest {

        @Test
        @DisplayName("누적 마이너스 10, 미션 100 → 10 을 갚고 90 만 받는다. 내역에는 원래 금액 100 을 남긴다")
        void missionExecution_paysAllDebt() {
            // given
            UserExperience userExp = experienceWithDebt(1, 0, 0, 10);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 100, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료");

            // then
            assertThat(userExp.getCurrentExp()).isEqualTo(90);
            assertThat(userExp.getTotalExp()).isEqualTo(90);
            assertThat(userExp.getExpDebt()).isZero();
            assertThat(result.getExpDeducted()).isEqualTo(10);
            assertThat(result.getExpDebt()).isZero();
            assertThat(savedHistory().getExpAmount()).isEqualTo(100);
        }

        @Test
        @DisplayName("누적 마이너스 150, 미션 100 → 전부 갚는 데 쓰고 50 이 남는다 (경험치는 늘지 않는다)")
        void missionExecution_paysPartOfDebt() {
            // given
            UserExperience userExp = experienceWithDebt(2, 0, 100, 150);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 100, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료");

            // then
            assertThat(userExp.getCurrentExp()).isZero();
            assertThat(userExp.getTotalExp()).isEqualTo(100);
            assertThat(userExp.getExpDebt()).isEqualTo(50);
            assertThat(result.getExpDeducted()).isEqualTo(100);
            assertThat(result.getExpDebt()).isEqualTo(50);
            assertThat(savedHistory().getExpAmount()).isEqualTo(100);
        }

        @Test
        @DisplayName("누적 마이너스가 없으면 그대로 받고 expDeducted 는 0 이다")
        void missionExecution_noDebt() {
            // given
            UserExperience userExp = experienceWithDebt(1, 20, 20, 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 30, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료");

            // then
            assertThat(userExp.getCurrentExp()).isEqualTo(50);
            assertThat(result.getExpDeducted()).isZero();
            assertThat(result.getExpDebt()).isZero();
        }

        @org.junit.jupiter.params.ParameterizedTest(name = "{0} 지급은 누적 마이너스를 갚지 않는다")
        @org.junit.jupiter.params.provider.EnumSource(
                value = ExpSourceType.class,
                names = {"MISSION_EXECUTION"},
                mode = org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE)
        @DisplayName("미션 수행(MISSION_EXECUTION) 이 아닌 출처는 누적 마이너스를 갚지 않고 전액을 받는다")
        void otherSources_doNotPayDebt(ExpSourceType sourceType) {
            // given
            UserExperience userExp = experienceWithDebt(1, 0, 0, 10);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(TEST_USER_ID, 20, sourceType, 3L, "보상");

            // then
            assertThat(userExp.getCurrentExp()).isEqualTo(20);
            assertThat(userExp.getTotalExp()).isEqualTo(20);
            assertThat(userExp.getExpDebt()).isEqualTo(10);
            assertThat(result.getExpDeducted()).isZero();
            assertThat(result.getExpDebt()).isEqualTo(10);
        }

        @Test
        @DisplayName("레벨업 판정은 갚고 남은 양으로 한다 — 90 에서 20 을 받아도 20 을 갚았으면 레벨 1 그대로")
        void levelUpUsesNetAmount() {
            // given — 기본 공식: 레벨 1 → 2 에 100 필요
            UserExperience userExp = experienceWithDebt(1, 90, 90, 20);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse result =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 20, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료");

            // then
            assertThat(userExp.getCurrentLevel()).isEqualTo(1);
            assertThat(userExp.getCurrentExp()).isEqualTo(90);
            assertThat(result.getExpDeducted()).isEqualTo(20);
            verify(eventPublisher, never()).publishEvent(any(UserLevelUpEvent.class));
            ExperienceHistory history = savedHistory();
            assertThat(history.getLevelBefore()).isEqualTo(1);
            assertThat(history.getLevelAfter()).isEqualTo(1);
        }

        @Test
        @DisplayName("카테고리 경험치는 차감 전 금액 그대로 더한다 (취소 때 전액을 빼므로)")
        void categoryExperienceGetsFullAmount() {
            // given
            UserExperience userExp = experienceWithDebt(1, 0, 0, 10);
            UserCategoryExperience categoryExp =
                    UserCategoryExperience.create(TEST_USER_ID, 7L, "운동", 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 7L))
                    .thenReturn(Optional.of(categoryExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            userExperienceService.addExperience(
                    TEST_USER_ID, 30, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료", 7L, "운동");

            // then
            assertThat(userExp.getCurrentExp()).isEqualTo(20);
            assertThat(categoryExp.getTotalExp()).isEqualTo(30L);
        }
    }

    @Nested
    @DisplayName("LUT-860 cancelExperience — 레벨을 내리지 않고 되돌린다")
    class CancelExperienceTest {

        @Test
        @DisplayName("현재 레벨 경험치에서 빼고, 금액 전체를 음수 내역(레벨 변화 없음)으로 남긴다")
        void withinCurrentExp() {
            // given
            UserExperience userExp = experienceWithDebt(3, 80, 380, 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse result =
                    userExperienceService.cancelExperience(
                            TEST_USER_ID,
                            30,
                            ExpSourceType.MISSION_CANCEL,
                            10L,
                            "미션 취소: 아침 스트레칭",
                            null,
                            "운동");

            // then
            assertThat(userExp.getCurrentLevel()).isEqualTo(3);
            assertThat(userExp.getCurrentExp()).isEqualTo(50);
            assertThat(userExp.getTotalExp()).isEqualTo(350);
            assertThat(userExp.getExpDebt()).isZero();
            assertThat(result.getCurrentLevel()).isEqualTo(3);
            assertThat(result.getCurrentExp()).isEqualTo(50);
            assertThat(result.getExpDebt()).isZero();
            assertThat(result.getExpDeducted()).isZero();

            ExperienceHistory history = savedHistory();
            assertThat(history.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(history.getSourceType()).isEqualTo(ExpSourceType.MISSION_CANCEL);
            assertThat(history.getSourceId()).isEqualTo(10L);
            assertThat(history.getExpAmount()).isEqualTo(-30);
            assertThat(history.getDescription()).isEqualTo("미션 취소: 아침 스트레칭");
            assertThat(history.getCategoryName()).isEqualTo("운동");
            assertThat(history.getLevelBefore()).isEqualTo(3);
            assertThat(history.getLevelAfter()).isEqualTo(3);
        }

        @Test
        @DisplayName("현재 레벨 경험치가 모자라도 레벨은 그대로 — 남는 양은 누적 마이너스로 쌓고 내역에는 전액을 남긴다")
        void beyondCurrentExp_keepsLevelAndAddsDebt() {
            // given — 레벨 3 에 막 올라와 현재 경험치 10
            UserExperience userExp = experienceWithDebt(3, 10, 260, 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(
                            List.of(
                                    createUserLevelConfig(1, 100, 0),
                                    createUserLevelConfig(2, 150, 100),
                                    createUserLevelConfig(3, 200, 250),
                                    createUserLevelConfig(4, 300, 450)));

            // when
            UserExperienceResponse result =
                    userExperienceService.cancelExperience(
                            TEST_USER_ID,
                            50,
                            ExpSourceType.MISSION_CANCEL,
                            10L,
                            "미션 취소",
                            null,
                            null);

            // then — subtractExperience 였다면 레벨 2 로 내려갔을 상황
            assertThat(userExp.getCurrentLevel()).isEqualTo(3);
            assertThat(userExp.getCurrentExp()).isZero();
            assertThat(userExp.getTotalExp()).isEqualTo(250);
            assertThat(userExp.getExpDebt()).isEqualTo(40);
            assertThat(result.getCurrentLevel()).isEqualTo(3);
            assertThat(result.getExpDebt()).isEqualTo(40);
            // 다음 레벨(4) 필요치는 레벨업 판정과 같은 행
            assertThat(result.getNextLevelRequiredExp()).isEqualTo(300);
            assertThat(savedHistory().getExpAmount()).isEqualTo(-50);
        }

        @Test
        @DisplayName("레벨이 바뀌지 않으므로 이벤트(레벨업·업적 체크)는 내지 않는다")
        void publishesNoEvents() {
            // given
            UserExperience userExp = experienceWithDebt(3, 80, 380, 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            userExperienceService.cancelExperience(
                    TEST_USER_ID, 30, ExpSourceType.MISSION_CANCEL, 10L, "미션 취소", null, null);

            // then
            org.mockito.Mockito.verifyNoInteractions(eventPublisher, diamondService);
        }

        @Test
        @DisplayName("카테고리 ID 가 있으면 카테고리 경험치도 전액 뺀다 (0 밑으로는 내려가지 않는다)")
        void subtractsCategoryExperience() {
            // given
            UserExperience userExp = experienceWithDebt(3, 80, 380, 0);
            UserCategoryExperience categoryExp =
                    UserCategoryExperience.create(TEST_USER_ID, 7L, "운동", 100);
            UserCategoryExperience smallCategoryExp =
                    UserCategoryExperience.create(TEST_USER_ID, 8L, "독서", 10);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 7L))
                    .thenReturn(Optional.of(categoryExp));
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 8L))
                    .thenReturn(Optional.of(smallCategoryExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            userExperienceService.cancelExperience(
                    TEST_USER_ID, 30, ExpSourceType.MISSION_CANCEL, 10L, "미션 취소", 7L, "운동");
            userExperienceService.cancelExperience(
                    TEST_USER_ID, 30, ExpSourceType.MISSION_CANCEL, 11L, "미션 취소", 8L, "독서");

            // then
            assertThat(categoryExp.getTotalExp()).isEqualTo(70L);
            assertThat(smallCategoryExp.getTotalExp()).isZero();
            verify(userCategoryExperienceRepository).save(categoryExp);
            verify(userCategoryExperienceRepository).save(smallCategoryExp);
        }

        @Test
        @DisplayName("카테고리 ID 가 없으면 카테고리 경험치는 건드리지 않는다 (마일스톤 보너스 회수)")
        void noCategoryId_categoryUntouched() {
            // given
            UserExperience userExp = experienceWithDebt(3, 80, 380, 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            userExperienceService.cancelExperience(
                    TEST_USER_ID,
                    20,
                    ExpSourceType.DAILY_MISSION_MILESTONE,
                    3L,
                    "미션 취소: 일일 체크 미션 3개 보너스",
                    null,
                    "기타");

            // then
            org.mockito.Mockito.verifyNoInteractions(userCategoryExperienceRepository);
            ExperienceHistory history = savedHistory();
            assertThat(history.getSourceType()).isEqualTo(ExpSourceType.DAILY_MISSION_MILESTONE);
            assertThat(history.getSourceId()).isEqualTo(3L);
            assertThat(history.getExpAmount()).isEqualTo(-20);
        }

        @Test
        @DisplayName("카테고리 경험치 행이 없으면 저장하지 않는다")
        void categoryRowMissing_notSaved() {
            // given
            UserExperience userExp = experienceWithDebt(3, 80, 380, 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 7L))
                    .thenReturn(Optional.empty());
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            userExperienceService.cancelExperience(
                    TEST_USER_ID, 30, ExpSourceType.MISSION_CANCEL, 10L, "미션 취소", 7L, "운동");

            // then
            verify(userCategoryExperienceRepository, never())
                    .save(any(UserCategoryExperience.class));
        }

        @Test
        @DisplayName("금액이 0 이면 내역도 카테고리 경험치도 건드리지 않는다")
        void zeroAmount_noHistory() {
            // given
            UserExperience userExp = experienceWithDebt(3, 80, 380, 5);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse result =
                    userExperienceService.cancelExperience(
                            TEST_USER_ID, 0, ExpSourceType.MISSION_CANCEL, 10L, "미션 취소", 7L, "운동");

            // then
            assertThat(userExp.getCurrentExp()).isEqualTo(80);
            assertThat(result.getExpDebt()).isEqualTo(5);
            verify(experienceHistoryRepository, never()).save(any(ExperienceHistory.class));
            org.mockito.Mockito.verifyNoInteractions(userCategoryExperienceRepository);
        }

        @Test
        @DisplayName("음수 금액은 IllegalArgumentException 이고 내역을 남기지 않는다")
        void negativeAmount_throws() {
            // given
            UserExperience userExp = experienceWithDebt(3, 80, 380, 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));

            // when & then
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () ->
                                    userExperienceService.cancelExperience(
                                            TEST_USER_ID,
                                            -1,
                                            ExpSourceType.MISSION_CANCEL,
                                            10L,
                                            "미션 취소",
                                            null,
                                            null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(userExp.getCurrentExp()).isEqualTo(80);
            verify(experienceHistoryRepository, never()).save(any(ExperienceHistory.class));
        }

        @Test
        @DisplayName("취소한 뒤 다음 미션을 받으면 누적 마이너스를 갚는다 — 취소 0 + 새 미션 100 으로 순합이 맞는다")
        void cancelThenGrant() {
            // given — 레벨 2, 현재 10
            UserExperience userExp = experienceWithDebt(2, 10, 110, 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(Collections.emptyList());

            // when
            UserExperienceResponse cancelled =
                    userExperienceService.cancelExperience(
                            TEST_USER_ID,
                            30,
                            ExpSourceType.MISSION_CANCEL,
                            10L,
                            "미션 취소",
                            null,
                            null);
            UserExperienceResponse granted =
                    userExperienceService.addExperience(
                            TEST_USER_ID, 100, ExpSourceType.MISSION_EXECUTION, 11L, "미션 완료");

            // then
            assertThat(cancelled.getExpDebt()).isEqualTo(20);
            assertThat(granted.getExpDeducted()).isEqualTo(20);
            assertThat(granted.getExpDebt()).isZero();
            assertThat(userExp.getCurrentExp()).isEqualTo(80);
            // 110 - 30 + 100
            assertThat(userExp.getTotalExp()).isEqualTo(180);
        }
    }

    @Nested
    @DisplayName("LUT-860 subtractExperience(expDeducted) — 지급을 되돌릴 때 갚았던 누적 마이너스를 돌려놓는다")
    class SubtractExperienceWithExpDeductedTest {

        @Test
        @DisplayName("갚았던 양은 다시 누적 마이너스로, 실제로 받은 양만 뺀다. 내역·카테고리는 전액으로 되돌린다")
        void restoresDebtAndSubtractsActualAmount() {
            // given — 누적 마이너스 30 에서 100 을 받아 70 만 들어온 상태(현재 170, 마이너스 0)
            UserExperience userExp = experienceWithDebt(3, 170, 370, 0);
            UserCategoryExperience categoryExp =
                    UserCategoryExperience.create(TEST_USER_ID, 7L, "운동", 100);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 7L))
                    .thenReturn(Optional.of(categoryExp));

            // when
            UserExperienceResponse result =
                    userExperienceService.subtractExperience(
                            TEST_USER_ID,
                            100,
                            30,
                            ExpSourceType.MISSION_EXECUTION,
                            1L,
                            "미션 완료 보상 - 경험치 환수",
                            7L,
                            "운동");

            // then
            assertThat(userExp.getCurrentExp()).isEqualTo(100);
            assertThat(userExp.getTotalExp()).isEqualTo(300);
            assertThat(userExp.getExpDebt()).isEqualTo(30);
            assertThat(result.getExpDebt()).isEqualTo(30);
            assertThat(savedHistory().getExpAmount()).isEqualTo(-100);
            assertThat(categoryExp.getTotalExp()).isZero();
        }

        @Test
        @DisplayName("전액을 갚는 데 썼던 지급을 되돌리면 경험치는 그대로, 누적 마이너스만 돌아온다")
        void allWasDeducted_onlyDebtRestored() {
            // given
            UserExperience userExp = experienceWithDebt(2, 0, 100, 50);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));

            // when
            userExperienceService.subtractExperience(
                    TEST_USER_ID, 100, 100, ExpSourceType.MISSION_EXECUTION, 1L, "환수", null, null);

            // then
            assertThat(userExp.getCurrentLevel()).isEqualTo(2);
            assertThat(userExp.getCurrentExp()).isZero();
            assertThat(userExp.getTotalExp()).isEqualTo(100);
            assertThat(userExp.getExpDebt()).isEqualTo(150);
        }

        @Test
        @DisplayName("expDeducted 가 금액보다 크면 금액까지만, 음수면 0 으로 본다")
        void expDeductedIsClamped() {
            // given
            UserExperience over = experienceWithDebt(2, 80, 180, 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(over));

            // when — 금액 50 인데 갚은 양 70 이라고 넘어온 경우
            userExperienceService.subtractExperience(
                    TEST_USER_ID, 50, 70, ExpSourceType.MISSION_EXECUTION, 1L, "환수", null, null);

            // then
            assertThat(over.getExpDebt()).isEqualTo(50);
            assertThat(over.getCurrentExp()).isEqualTo(80);

            // given
            UserExperience negative = experienceWithDebt(2, 80, 180, 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(negative));

            // when
            userExperienceService.subtractExperience(
                    TEST_USER_ID, 50, -10, ExpSourceType.MISSION_EXECUTION, 1L, "환수", null, null);

            // then
            assertThat(negative.getExpDebt()).isZero();
            assertThat(negative.getCurrentExp()).isEqualTo(30);
        }

        @Test
        @DisplayName("레벨 다운 판정은 실제로 받은 양으로 한다 — 대부분 갚는 데 썼으면 레벨은 내려가지 않는다")
        void levelDownUsesActualAmount() {
            // given — 현재 10, 금액 100 중 95 를 갚는 데 썼다 → 실제 5 만 뺀다
            UserExperience userExp = experienceWithDebt(3, 10, 260, 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));

            // when
            userExperienceService.subtractExperience(
                    TEST_USER_ID, 100, 95, ExpSourceType.MISSION_EXECUTION, 1L, "환수", null, null);

            // then
            assertThat(userExp.getCurrentLevel()).isEqualTo(3);
            assertThat(userExp.getCurrentExp()).isEqualTo(5);
            assertThat(userExp.getTotalExp()).isEqualTo(255);
            assertThat(userExp.getExpDebt()).isEqualTo(95);
        }

        @Test
        @DisplayName("기존 7개 인자 버전은 expDeducted 0 과 같다 — 예전처럼 전액을 빼고 누적 마이너스는 건드리지 않는다")
        void sevenArgVersion_sameAsBefore() {
            // given
            UserExperience legacy = experienceWithDebt(3, 150, 350, 12);
            UserExperience explicitZero = experienceWithDebt(3, 150, 350, 12);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(legacy), Optional.of(explicitZero));

            // when
            UserExperienceResponse legacyResult =
                    userExperienceService.subtractExperience(
                            TEST_USER_ID,
                            50,
                            ExpSourceType.MISSION_EXECUTION,
                            1L,
                            "환수",
                            null,
                            "운동");
            UserExperienceResponse explicitResult =
                    userExperienceService.subtractExperience(
                            TEST_USER_ID,
                            50,
                            0,
                            ExpSourceType.MISSION_EXECUTION,
                            1L,
                            "환수",
                            null,
                            "운동");

            // then
            assertThat(legacy.getCurrentExp()).isEqualTo(100);
            assertThat(legacy.getTotalExp()).isEqualTo(300);
            assertThat(legacy.getExpDebt()).isEqualTo(12);
            assertThat(explicitZero.getCurrentExp()).isEqualTo(legacy.getCurrentExp());
            assertThat(explicitZero.getTotalExp()).isEqualTo(legacy.getTotalExp());
            assertThat(explicitZero.getExpDebt()).isEqualTo(legacy.getExpDebt());
            assertThat(explicitResult.getCurrentExp()).isEqualTo(legacyResult.getCurrentExp());
            assertThat(explicitResult.getExpDebt()).isEqualTo(legacyResult.getExpDebt());
        }

        @Test
        @DisplayName("기존 7개 인자 버전은 예전처럼 레벨을 내릴 수 있다 (취소와 다른 점)")
        void sevenArgVersion_stillLevelsDown() {
            // given
            UserExperience userExp = experienceWithDebt(3, 10, 260, 0);
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(userExp));
            when(userLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(
                            List.of(
                                    createUserLevelConfig(1, 100, 0),
                                    createUserLevelConfig(2, 150, 100),
                                    createUserLevelConfig(3, 200, 250)));

            // when
            userExperienceService.subtractExperience(
                    TEST_USER_ID, 50, ExpSourceType.MISSION_EXECUTION, 1L, "환수", null, null);

            // then — 총 210 → 레벨 2(누적 100) + 110
            assertThat(userExp.getCurrentLevel()).isEqualTo(2);
            assertThat(userExp.getCurrentExp()).isEqualTo(110);
            assertThat(userExp.getTotalExp()).isEqualTo(210);
            assertThat(userExp.getExpDebt()).isZero();
        }
    }

    @Nested
    @DisplayName("LUT-860 sumExperienceBySource — 출처별 기간 순합")
    class SumExperienceBySourceTest {

        private final LocalDateTime from = LocalDateTime.of(2026, 11, 25, 15, 0);
        private final LocalDateTime to = LocalDateTime.of(2026, 11, 26, 15, 0);

        @Test
        @DisplayName("저장소의 순합을 int 로 돌려준다")
        void returnsSum() {
            when(experienceHistoryRepository.sumExpByUserIdAndSourceAndPeriod(
                            TEST_USER_ID, ExpSourceType.DAILY_MISSION_MILESTONE, 3L, from, to))
                    .thenReturn(20L);

            int result =
                    userExperienceService.sumExperienceBySource(
                            TEST_USER_ID, ExpSourceType.DAILY_MISSION_MILESTONE, 3L, from, to);

            assertThat(result).isEqualTo(20);
        }

        @Test
        @DisplayName("저장소가 null 을 주면 0 이다")
        void nullIsZero() {
            when(experienceHistoryRepository.sumExpByUserIdAndSourceAndPeriod(
                            TEST_USER_ID, ExpSourceType.DAILY_MISSION_MILESTONE, 5L, from, to))
                    .thenReturn(null);

            int result =
                    userExperienceService.sumExperienceBySource(
                            TEST_USER_ID, ExpSourceType.DAILY_MISSION_MILESTONE, 5L, from, to);

            assertThat(result).isZero();
        }
    }
}
