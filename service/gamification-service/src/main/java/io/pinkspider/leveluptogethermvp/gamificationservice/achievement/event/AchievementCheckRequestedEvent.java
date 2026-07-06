package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.event;

import java.util.List;

/**
 * 커밋 후 업적 체크 요청 이벤트 (gamification 서비스 내부 이벤트).
 *
 * <p>checkAchievementsByDataSource 는 REQUIRES_NEW 로 실행되어 발행 트랜잭션의 미커밋 데이터를 읽을 수
 * 없다(QA-178). 경험치 지급·출석 처리처럼 소스 데이터를 갱신한 트랜잭션 안에서 인라인으로 체크하면 갱신 전
 * 값으로 판정되어 업적 완료가 다음 체크 시점(홈 sync 등)까지 지연된다. 이 이벤트를 발행하면
 * AchievementEventListener 가 AFTER_COMMIT 에 체크하므로 방금 반영된 값이 판정에 포함된다.
 *
 * @param userId 사용자 ID
 * @param dataSources 체크할 데이터 소스 목록 (USER_EXPERIENCE, USER_CATEGORY_EXPERIENCE, USER_STATS 등)
 */
public record AchievementCheckRequestedEvent(String userId, List<String> dataSources) {
}
