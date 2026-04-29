package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;

/**
 * 업적 달성 조건 체크를 위한 Strategy 인터페이스
 * 각 데이터 소스별로 구현체를 제공합니다.
 */
public interface AchievementCheckStrategy {

    /**
     * 이 전략이 처리하는 데이터 소스 코드를 반환합니다.
     *
     * @return 데이터 소스 코드 (예: USER_STATS, USER_EXPERIENCE 등)
     */
    String getDataSource();

    /**
     * 지정된 사용자와 데이터 필드에 대한 현재 값을 조회합니다.
     *
     * @param userId    사용자 ID
     * @param dataField 데이터 필드명
     * @return 현재 값 (Number 또는 Boolean)
     */
    Object fetchCurrentValue(String userId, String dataField);

    /**
     * Achievement 컨텍스트 기반으로 현재 값을 조회합니다.
     * 일반 Strategy는 dataField만 사용하지만, USER_CATEGORY_EXPERIENCE 처럼 추가 컨텍스트(missionCategoryId)가
     * 필요한 경우 이 메서드를 오버라이드해서 Achievement 정보를 사용합니다.
     */
    default Object fetchCurrentValue(String userId, Achievement achievement) {
        return fetchCurrentValue(userId, achievement.getCheckLogicDataField());
    }

    /**
     * 업적 달성 조건을 체크합니다.
     *
     * @param userId      사용자 ID
     * @param achievement 체크할 업적
     * @return 조건 충족 여부
     */
    boolean checkCondition(String userId, Achievement achievement);
}
