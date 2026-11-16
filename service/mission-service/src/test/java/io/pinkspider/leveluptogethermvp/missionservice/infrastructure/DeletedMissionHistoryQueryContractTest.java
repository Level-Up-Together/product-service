package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.Query;

/**
 * LUT-789 결정: 지운 미션(소프트 삭제)의 수행 기록은 과거 사실이라 남긴다.
 *
 * <p>주간·월간 캘린더, 월간 리포트(예정·완료 수, 분포, EXP 합), 기록 시간 수정의 겹침 검사가 모두 같은 기준 — 지운 미션의 기록을 포함 — 으로 동작해야 한다.
 * 한 곳에만 삭제 조건을 넣으면 캘린더에는 안 보이는 기록 때문에 시간 수정이 거절되는 식으로 어긋난다. 같은 정의: 소프트 삭제 도입 (2026-02-23, 피드 유지 =
 * 수행 기록 보존) · QA-158(삭제해도 클리어 이력 유효) · LUT-566(미션 상태로 과거 기록을 소급 제외하지 않음).
 *
 * <p>단위 테스트는 mock 리포지토리라 JPQL 조건을 볼 수 없어 {@link Query} 어노테이션으로 검증한다. 규칙을 바꾸려면 이 목록의 쿼리를 모두 함께 바꿀 것.
 */
class DeletedMissionHistoryQueryContractTest {

    /** 캘린더·리포트·겹침 검사가 쓰는 기록 조회 — 두 리포지토리에 같은 이름으로 있다 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "findCompletedByUserIdAndCompletedAtBetween", // 주간·월간 캘린더, 월간 리포트 분포
                "findCompletedByUserIdAndDateRange", // 기록 시간 수정의 겹침 검사
                "sumExpEarnedByUserIdAndDateRange", // 월간 캘린더 EXP 합
                "countScheduledInPeriod", // 월간 리포트 예정 수
                "countCompletedInPeriod" // 월간 리포트 완료 수
            })
    @DisplayName("기록 조회 쿼리에 미션 삭제 조건이 없다 (지운 미션의 기록 포함)")
    void historyQueriesIncludeDeletedMissions(String methodName) {
        for (Class<?> repository :
                List.of(MissionExecutionRepository.class, DailyMissionInstanceRepository.class)) {
            List<Method> methods =
                    Arrays.stream(repository.getDeclaredMethods())
                            .filter(m -> m.getName().equals(methodName))
                            .toList();
            assertThat(methods).as("%s.%s", repository.getSimpleName(), methodName).isNotEmpty();
            for (Method method : methods) {
                Query query = method.getAnnotation(Query.class);
                assertThat(query)
                        .as("%s.%s 는 @Query 여야 한다", repository.getSimpleName(), methodName)
                        .isNotNull();
                assertThat(query.value().replace(" ", "").toLowerCase())
                        .as(
                                "%s.%s — 지운 미션의 기록을 빼면 캘린더·리포트·겹침 검사 기준이 어긋난다 (LUT-789)",
                                repository.getSimpleName(), methodName)
                        .doesNotContain("isdeleted")
                        .doesNotContain("is_deleted");
            }
        }
    }
}
