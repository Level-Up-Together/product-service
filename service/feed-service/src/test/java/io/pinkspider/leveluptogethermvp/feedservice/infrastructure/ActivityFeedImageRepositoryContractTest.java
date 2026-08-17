package io.pinkspider.leveluptogethermvp.feedservice.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * LUT-385 회귀 가드: deleteByFeedId 는 반드시 벌크 삭제(@Query)여야 한다.
 *
 * <p>파생 삭제(deleteBy... 쿼리 메서드)로 되돌리면 엔티티 제거가 ActionQueue에 쌓이고, Hibernate flush
 * 순서상 INSERT가 DELETE보다 먼저 실행된다. updateFeedImagesByExecutionId 의 "전체 교체(삭제 후
 * 재등록)"에서 기존 행과 (feed_id, sort_order) 유니크 제약(uk_activity_feed_image_sort)이 충돌해, 이미
 * 이미지가 있는 피드의 이미지 재동기화가 전부 실패한다(미션 상세 재등록 시 홈 피드 이미지 미반영).
 * 단위 테스트는 mock 리포지토리라 flush 순서를 재현할 수 없어 어노테이션 계약으로 검증한다.
 */
class ActivityFeedImageRepositoryContractTest {

    @Test
    @DisplayName("deleteByFeedId는 즉시 실행되는 벌크 @Query 삭제다")
    void deleteByFeedId_mustBeBulkQueryDelete() throws NoSuchMethodException {
        Method method = ActivityFeedImageRepository.class.getMethod("deleteByFeedId", Long.class);

        Query query = method.getAnnotation(Query.class);
        assertThat(query)
            .as("deleteByFeedId 가 파생 삭제로 돌아가면 delete-then-insert 교체가 "
                + "uk_activity_feed_image_sort 충돌로 실패한다 (LUT-385)")
            .isNotNull();
        assertThat(query.value().toUpperCase()).startsWith("DELETE");

        Modifying modifying = method.getAnnotation(Modifying.class);
        assertThat(modifying).isNotNull();
        assertThat(modifying.flushAutomatically())
            .as("벌크 삭제 전에 영속성 컨텍스트의 선행 변경을 flush 해야 순서가 보장된다")
            .isTrue();
    }
}
