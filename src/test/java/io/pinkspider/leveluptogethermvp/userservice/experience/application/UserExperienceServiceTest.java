package io.pinkspider.leveluptogethermvp.userservice.experience.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.ExperienceHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ExpSourceType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.LevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.infrastructure.LevelConfigRepository;
import io.pinkspider.leveluptogethermvp.userservice.experience.domain.dto.UserExperienceResponse;
import java.lang.reflect.Field;
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
    private LevelConfigRepository levelConfigRepository;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private UserExperienceService userExperienceService;

    private static final String TEST_USER_ID = "test-user-123";

    private void setUserExperienceId(UserExperience userExp, Long id) {
        try {
            Field idField = UserExperience.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(userExp, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UserExperience createTestUserExperience(Long id, String userId, int level, int currentExp, int totalExp) {
        UserExperience userExp = UserExperience.builder()
            .userId(userId)
            .currentLevel(level)
            .currentExp(currentExp)
            .totalExp(totalExp)
            .build();
        setUserExperienceId(userExp, id);
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
            when(levelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(Collections.emptyList());
            when(levelConfigRepository.findByLevel(1)).thenReturn(Optional.of(createLevelConfig(1, 100, 0)));

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
            when(levelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(Collections.emptyList());
            when(levelConfigRepository.findByLevel(1)).thenReturn(Optional.of(createLevelConfig(1, 100, 0)));

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
            when(levelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(Collections.emptyList());
            when(levelConfigRepository.findByLevel(2)).thenReturn(Optional.empty());

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
            when(levelConfigRepository.findByLevel(5)).thenReturn(Optional.of(createLevelConfig(5, 300, 700)));

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
            when(levelConfigRepository.findByLevel(1)).thenReturn(Optional.of(createLevelConfig(1, 100, 0)));

            // when
            UserExperienceResponse result = userExperienceService.getUserExperience(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCurrentLevel()).isEqualTo(1);
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
            when(levelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(
                createLevelConfig(1, 100, 0),
                createLevelConfig(2, 150, 100),
                createLevelConfig(3, 200, 250)
            ));
            when(levelConfigRepository.findByLevel(2)).thenReturn(Optional.of(createLevelConfig(2, 150, 100)));

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
            when(levelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(
                createLevelConfig(1, 100, 0),
                createLevelConfig(2, 150, 100)
            ));
            when(levelConfigRepository.findByLevel(1)).thenReturn(Optional.of(createLevelConfig(1, 100, 0)));

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

            when(levelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(configs);

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
}
