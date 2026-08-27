package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.enums.DiamondPurchaseStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * LUT-431: {@code (:param IS NULL OR ...)} JPQL 이 PostgreSQL 42P18(파라미터 타입 추론 실패)로 죽어
 * 필터 적용 여부를 boolean 플래그로 분리했다. default 메서드가 null 여부를 플래그로 정확히 변환하는지 고정한다.
 * (타입 추론 실패 자체는 H2 계열 테스트로 재현되지 않아 dev/prod 조회로 검증)
 */
class DiamondBundlePurchaseRepositoryTest {

    private final DiamondBundlePurchaseRepository repository =
        mock(DiamondBundlePurchaseRepository.class,
            withSettings().defaultAnswer(CALLS_REAL_METHODS));

    private final Pageable pageable = PageRequest.of(0, 20);

    @Test
    @DisplayName("필터 전부 미지정이면 모든 플래그가 false 로 위임된다 (기본 목록 조회 — LUT-431 재현 케이스)")
    void search_allNull_delegatesAllFlagsFalse() {
        repository.search(null, null, null, null, null, pageable);

        verify(repository).searchInternal(
            eq(false), isNull(), eq(false), isNull(), eq(false), isNull(),
            eq(false), isNull(), eq(false), isNull(), eq(pageable));
    }

    @Test
    @DisplayName("지정된 필터만 플래그 true 로 위임된다")
    void search_partialFilters_delegatesMatchingFlags() {
        LocalDateTime startAt = LocalDateTime.of(2026, 8, 1, 0, 0);

        repository.search(startAt, null, "IOS", null, DiamondPurchaseStatus.PAID, pageable);

        verify(repository).searchInternal(
            eq(true), eq(startAt), eq(false), isNull(), eq(true), eq("IOS"),
            eq(false), isNull(), eq(true), eq(DiamondPurchaseStatus.PAID), eq(pageable));
    }

    @Test
    @DisplayName("닉네임 매칭 변형도 동일하게 플래그 위임된다")
    void searchWithUsers_delegatesFlags() {
        List<String> userIds = List.of("user-1", "user-2");

        repository.searchWithUsers(null, null, null, 3L, null, userIds, pageable);

        verify(repository).searchWithUsersInternal(
            eq(false), isNull(), eq(false), isNull(), eq(false), isNull(),
            eq(true), eq(3L), eq(false), isNull(), eq(userIds), eq(pageable));
    }
}
