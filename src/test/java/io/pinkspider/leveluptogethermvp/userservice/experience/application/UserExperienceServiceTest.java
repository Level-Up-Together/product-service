package io.pinkspider.leveluptogethermvp.userservice.experience.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.ExperienceHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserCategoryExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ExpSourceType;
import io.pinkspider.global.cache.LevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserCategoryExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.levelconfig.domain.entity.LevelConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.levelconfig.infrastructure.LevelConfigRepository;
import io.pinkspider.leveluptogethermvp.userservice.experience.domain.dto.UserExperienceResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class UserExperienceServiceTest {

    @Mock
    private UserExperienceRepository userExperienceRepository;

    @Mock
    private ExperienceHistoryRepository experienceHistoryRepository;

    @Mock
    private UserCategoryExperienceRepository userCategoryExperienceRepository;

    @Mock
    private LevelConfigCacheService levelConfigCacheService;

    @Mock
    private LevelConfigRepository levelConfigRepository;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private UserExperienceService userExperienceService;

    private static final String TEST_USER_ID = "test-user-123";

    private UserExperience createTestUserExperience(Long id, String userId, int level, int currentExp, int totalExp) {
        UserExperience userExp = UserExperience.builder()
            .userId(userId)
            .currentLevel(level)
            .currentExp(currentExp)
            .totalExp(totalExp)
            .build();
        setId(userExp, id);
        return userExp;
    }

    private LevelConfig createLevelConfig(int level, int requiredExp, Integer cumulativeExp) {
        return LevelConfig.builder()
            .level(level)
            .requiredExp(requiredExp)
            .cumulativeExp(cumulativeExp)
            .title("레벨 " + level)
            .description("레벨 " + level + " 설명")
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

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(Collections.emptyList());
            when(levelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(createLevelConfig(1, 100, 0));

            // when
            UserExperienceResponse result = userExperienceService.addExperience(
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
            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(Collections.emptyList());
            when(levelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(createLevelConfig(1, 100, 0));

            // when
            UserExperienceResponse result = userExperienceService.addExperience(
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

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(Collections.emptyList());
            when(levelConfigCacheService.getLevelConfigByLevel(2)).thenReturn(null);

            // when
            UserExperienceResponse result = userExperienceService.addExperience(
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

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getLevelConfigByLevel(5)).thenReturn(createLevelConfig(5, 300, 700));

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
            when(levelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(createLevelConfig(1, 100, 0));

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
            when(userExperienceRepository.save(any(UserExperience.class))).thenAnswer(invocation -> {
                UserExperience saved = invocation.getArgument(0);
                setId(saved, 1L);
                return saved;
            });
            when(levelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(createLevelConfig(1, 100, 0));

            // when
            UserExperienceResponse result = userExperienceService.getUserExperience(TEST_USER_ID);

            // then
            assertThat(result.getCurrentLevel()).isEqualTo(1);
            assertThat(result.getCurrentExp()).isEqualTo(0);
            assertThat(result.getTotalExp()).isEqualTo(0);
            assertThat(result.getNextLevelRequiredExp()).isEqualTo(100);
        }

        @Test
        @DisplayName("LevelConfig가 없어도 기본 공식으로 다음 레벨 경험치를 계산한다")
        void getUserExperience_noLevelConfig_usesDefaultFormula() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 3, 50, 300);

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getLevelConfigByLevel(3)).thenReturn(null); // config 없음

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
    @DisplayName("getOrCreateUserExperience 테스트")
    class GetOrCreateUserExperienceTest {

        @Test
        @DisplayName("기존 경험치가 있으면 그대로 반환한다")
        void getOrCreateUserExperience_existingUser() {
            // given
            UserExperience existingExp = createTestUserExperience(1L, TEST_USER_ID, 5, 100, 500);

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingExp));

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
            when(userExperienceRepository.save(any(UserExperience.class))).thenAnswer(invocation -> {
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
            ExperienceHistory history1 = ExperienceHistory.builder()
                .userId(TEST_USER_ID)
                .sourceType(ExpSourceType.MISSION_EXECUTION)
                .expAmount(50)
                .description("미션 완료")
                .build();
            ExperienceHistory history2 = ExperienceHistory.builder()
                .userId(TEST_USER_ID)
                .sourceType(ExpSourceType.EVENT)
                .expAmount(10)
                .description("출석 체크")
                .build();
            Page<ExperienceHistory> page = new PageImpl<>(List.of(history1, history2), pageable, 2);

            when(experienceHistoryRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID, pageable))
                .thenReturn(page);

            // when
            Page<ExperienceHistory> result = userExperienceService.getExperienceHistory(TEST_USER_ID, pageable);

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

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));

            // when
            UserExperienceResponse result = userExperienceService.subtractExperience(
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

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(List.of(
                createLevelConfig(1, 100, 0),
                createLevelConfig(2, 150, 100),
                createLevelConfig(3, 200, 250)
            ));
            when(levelConfigCacheService.getLevelConfigByLevel(2)).thenReturn(createLevelConfig(2, 150, 100));

            // when
            UserExperienceResponse result = userExperienceService.subtractExperience(
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

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(List.of(
                createLevelConfig(1, 100, 0),
                createLevelConfig(2, 150, 100)
            ));
            when(levelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(createLevelConfig(1, 100, 0));

            // when
            UserExperienceResponse result = userExperienceService.subtractExperience(
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
            List<LevelConfig> configs = List.of(
                createLevelConfig(1, 100, 0),
                createLevelConfig(2, 150, 100),
                createLevelConfig(3, 200, 250)
            );

            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(configs);

            // when
            List<LevelConfig> result = userExperienceService.getAllLevelConfigs();

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
            LevelConfig newConfig = createLevelConfig(10, 500, 2000);

            when(levelConfigRepository.findByLevel(10)).thenReturn(Optional.empty());
            when(levelConfigRepository.save(any(LevelConfig.class))).thenReturn(newConfig);

            // when
            LevelConfig result = userExperienceService.createOrUpdateLevelConfig(
                10, 500, 2000, "레벨 10", "상급 모험가");

            // then
            assertThat(result).isNotNull();
            verify(levelConfigRepository).save(any(LevelConfig.class));
        }

        @Test
        @DisplayName("기존 레벨 설정을 업데이트한다")
        void createOrUpdateLevelConfig_update() {
            // given
            LevelConfig existingConfig = createLevelConfig(5, 300, 700);

            when(levelConfigRepository.findByLevel(5)).thenReturn(Optional.of(existingConfig));
            when(levelConfigRepository.save(any(LevelConfig.class))).thenReturn(existingConfig);

            // when
            LevelConfig result = userExperienceService.createOrUpdateLevelConfig(
                5, 350, 750, "업데이트된 레벨 5", "업데이트된 설명");

            // then
            assertThat(result.getRequiredExp()).isEqualTo(350);
            verify(levelConfigRepository).save(existingConfig);
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

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(Collections.emptyList());
            when(levelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(createLevelConfig(1, 100, 0));

            // when
            UserExperienceResponse result = userExperienceService.addExperience(
                TEST_USER_ID, 30, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료", "건강");

            // then
            assertThat(result).isNotNull();
            assertThat(userExp.getCurrentExp()).isEqualTo(80);
            verify(experienceHistoryRepository).save(any(ExperienceHistory.class));
            // categoryId가 null이므로 카테고리 경험치는 업데이트되지 않음
            verify(userCategoryExperienceRepository, never()).save(any(UserCategoryExperience.class));
        }

        @Test
        @DisplayName("카테고리ID와 카테고리명을 포함하여 경험치를 추가하면 카테고리 경험치도 업데이트된다")
        void addExperience_withCategoryIdAndName_updatesCategoryExp() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 50, 50);
            UserCategoryExperience categoryExp = UserCategoryExperience.create(TEST_USER_ID, 1L, "건강", 100);

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(Collections.emptyList());
            when(levelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(createLevelConfig(1, 100, 0));
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(categoryExp));

            // when
            UserExperienceResponse result = userExperienceService.addExperience(
                TEST_USER_ID, 30, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료", 1L, "건강");

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

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(Collections.emptyList());
            when(levelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(createLevelConfig(1, 100, 0));
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                .thenReturn(Optional.empty());

            // when
            UserExperienceResponse result = userExperienceService.addExperience(
                TEST_USER_ID, 30, ExpSourceType.MISSION_EXECUTION, 1L, "미션 완료", 1L, "건강");

            // then
            assertThat(result).isNotNull();
            verify(userCategoryExperienceRepository).save(any(UserCategoryExperience.class));
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

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getLevelConfigByLevel(3)).thenReturn(createLevelConfig(3, 200, 250));

            // when
            UserExperienceResponse result = userExperienceService.subtractExperience(
                TEST_USER_ID, 50, ExpSourceType.MISSION_EXECUTION, 1L, "보상 취소", "건강");

            // then
            assertThat(result).isNotNull();
            verify(experienceHistoryRepository).save(any(ExperienceHistory.class));
            // categoryId가 null이므로 카테고리 경험치는 차감되지 않음
            verify(userCategoryExperienceRepository, never()).findByUserIdAndCategoryId(any(), any());
        }

        @Test
        @DisplayName("카테고리ID를 포함하여 경험치를 차감하면 카테고리 경험치도 차감된다")
        void subtractExperience_withCategoryId_subtractsCategoryExp() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 3, 150, 350);
            UserCategoryExperience categoryExp = UserCategoryExperience.create(TEST_USER_ID, 1L, "건강", 100);

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getLevelConfigByLevel(3)).thenReturn(createLevelConfig(3, 200, 250));
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(categoryExp));

            // when
            UserExperienceResponse result = userExperienceService.subtractExperience(
                TEST_USER_ID, 50, ExpSourceType.MISSION_EXECUTION, 1L, "보상 취소", 1L, "건강");

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
            UserCategoryExperience categoryExp = UserCategoryExperience.create(TEST_USER_ID, 1L, "건강", 30);

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getLevelConfigByLevel(3)).thenReturn(createLevelConfig(3, 200, 250));
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(categoryExp));

            // when
            UserExperienceResponse result = userExperienceService.subtractExperience(
                TEST_USER_ID, 50, ExpSourceType.MISSION_EXECUTION, 1L, "보상 취소", 1L, "건강");

            // then
            assertThat(result).isNotNull();
            assertThat(categoryExp.getTotalExp()).isEqualTo(0); // max(0, 30-50) = 0
        }
    }

    @Nested
    @DisplayName("processLevelUp 테스트 (LevelConfig 기반)")
    class ProcessLevelUpWithConfigTest {

        @Test
        @DisplayName("LevelConfig가 있을 때 레벨업한다")
        void addExperience_levelUpWithConfig() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 1, 90, 90);
            // 새 로직: 다음 레벨의 required_exp를 체크하므로, level 2의 required_exp가 100이어야 110 exp로 레벨업 가능
            List<LevelConfig> configs = List.of(
                createLevelConfig(1, 0, 0),       // level 1 (시작 레벨)
                createLevelConfig(2, 100, 100),   // level 2 도달에 100 exp 필요
                createLevelConfig(3, 150, 250)    // level 3 도달에 150 exp 추가 필요
            );

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(configs);
            when(levelConfigCacheService.getMaxLevel()).thenReturn(3);
            // getNextLevelRequiredExp에서 레벨업 후 현재 레벨(2)의 config를 조회함
            when(levelConfigCacheService.getLevelConfigByLevel(anyInt())).thenAnswer(invocation -> {
                int level = invocation.getArgument(0);
                return configs.stream().filter(c -> c.getLevel().equals(level)).findFirst().orElse(null);
            });

            // when - 20 exp 추가하면 총 110 exp, level 2의 required_exp(100)을 충족하므로 레벨업
            UserExperienceResponse result = userExperienceService.addExperience(
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
            List<LevelConfig> configs = List.of(
                createLevelConfig(1, 100, 0),
                createLevelConfig(2, 150, 100),
                createLevelConfig(3, 200, 250)
            );

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(configs);
            when(levelConfigCacheService.getLevelConfigByLevel(4)).thenReturn(null);

            // when
            UserExperienceResponse result = userExperienceService.addExperience(
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
            List<LevelConfig> configs = List.of(
                createLevelConfig(1, 0, 0),       // level 1 (시작 레벨)
                createLevelConfig(2, 100, 100),   // level 2 도달에 100 exp 필요
                createLevelConfig(3, 150, 250),   // level 3 도달에 150 exp 추가 필요
                createLevelConfig(4, 200, 450)    // level 4 도달에 200 exp 추가 필요
            );

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(configs);
            when(levelConfigCacheService.getMaxLevel()).thenReturn(10);
            // getNextLevelRequiredExp에서 최종 레벨의 config를 조회함
            when(levelConfigCacheService.getLevelConfigByLevel(anyInt())).thenAnswer(invocation -> {
                int level = invocation.getArgument(0);
                return configs.stream().filter(c -> c.getLevel().equals(level)).findFirst().orElse(null);
            });

            // when - 300 경험치 추가 (레벨 1 -> 2 -> 3까지 도달)
            UserExperienceResponse result = userExperienceService.addExperience(
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
        @DisplayName("레벨 다운 시 누적 경험치가 없는 LevelConfig도 처리한다")
        void subtractExperience_levelDownWithNullCumulativeExp() {
            // given
            UserExperience userExp = createTestUserExperience(1L, TEST_USER_ID, 3, 10, 260);
            LevelConfig config1 = LevelConfig.builder()
                .level(1)
                .requiredExp(100)
                .cumulativeExp(null)  // 누적 경험치 없음
                .build();
            LevelConfig config2 = LevelConfig.builder()
                .level(2)
                .requiredExp(150)
                .cumulativeExp(null)  // 누적 경험치 없음
                .build();

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));
            when(levelConfigCacheService.getAllLevelConfigs()).thenReturn(List.of(config1, config2));
            when(levelConfigCacheService.getLevelConfigByLevel(2)).thenReturn(config2);

            // when
            UserExperienceResponse result = userExperienceService.subtractExperience(
                TEST_USER_ID, 100, ExpSourceType.MISSION_EXECUTION, 1L, "보상 취소");

            // then
            assertThat(result).isNotNull();
        }
    }
}
