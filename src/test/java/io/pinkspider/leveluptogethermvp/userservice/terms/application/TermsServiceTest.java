package io.pinkspider.leveluptogethermvp.userservice.terms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.RecentTermsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.TermAgreementsByUserResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.TermsRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TermsServiceTest {

    @Mock
    private TermsRepository termsRepository;

    @InjectMocks
    private TermsService termsService;

    private static final String TEST_USER_ID = "test-user-123";

    // Object[] 형태로 RecentTerms 데이터 생성
    // 컬럼 순서: term_id(0), term_title(1), code(2), type(3), is_required(4), version_id(5), version(6), created_at(7), content(8)
    private Object[] createRawRecentTerms(Long termId, String termTitle, boolean isRequired) {
        return new Object[] {
            termId,           // term_id
            termTitle,        // term_title
            "TERMS_001",      // code
            "REQUIRED",       // type
            isRequired,       // is_required
            1L,               // version_id
            "1.0",            // version
            "2024-01-01",     // created_at
            "약관 내용"        // content
        };
    }

    // Object[] 형태로 TermAgreement 데이터 생성
    // 컬럼 순서: term_id(0), term_title(1), is_required(2), latest_version_id(3), version(4), is_agreed(5), agreed_at(6)
    private Object[] createRawTermAgreement(Long termId, String termTitle, boolean isAgreed) {
        return new Object[] {
            termId,                           // term_id
            termTitle,                        // term_title
            true,                             // is_required
            1L,                               // latest_version_id
            "1.0",                            // version
            isAgreed,                         // is_agreed
            isAgreed ? "2024-01-01" : null    // agreed_at
        };
    }

    // Helper method to create List<Object[]>
    private List<Object[]> createObjectArrayList(Object[]... arrays) {
        List<Object[]> result = new ArrayList<>();
        for (Object[] array : arrays) {
            result.add(array);
        }
        return result;
    }

    @Nested
    @DisplayName("getRecentAllTerms 테스트")
    class GetRecentAllTermsTest {

        @Test
        @DisplayName("최근 약관 목록을 조회한다")
        void getRecentAllTerms_success() {
            // given
            Object[] term1 = createRawRecentTerms(1L, "이용약관", true);
            Object[] term2 = createRawRecentTerms(2L, "개인정보처리방침", true);

            doReturn(createObjectArrayList(term1, term2)).when(termsRepository).getRecentAllTermsRaw();

            // when
            List<RecentTermsResponseDto> result = termsService.getRecentAllTerms();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getTermId()).isEqualTo("1");
            assertThat(result.get(0).getTermTitle()).isEqualTo("이용약관");
            assertThat(result.get(1).getTermId()).isEqualTo("2");
            assertThat(result.get(1).getTermTitle()).isEqualTo("개인정보처리방침");
            verify(termsRepository).getRecentAllTermsRaw();
        }

        @Test
        @DisplayName("약관이 없으면 빈 목록을 반환한다")
        void getRecentAllTerms_empty() {
            // given
            when(termsRepository.getRecentAllTermsRaw()).thenReturn(Collections.emptyList());

            // when
            List<RecentTermsResponseDto> result = termsService.getRecentAllTerms();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTermAgreementsByUser 테스트")
    class GetTermAgreementsByUserTest {

        @Test
        @DisplayName("사용자별 약관 동의 내역을 조회한다")
        void getTermAgreementsByUser_success() {
            // given
            Object[] agreement = createRawTermAgreement(1L, "이용약관", true);

            doReturn(createObjectArrayList(agreement)).when(termsRepository).getTermAgreementsByUserRaw(TEST_USER_ID);

            // when
            List<TermAgreementsByUserResponseDto> result = termsService.getTermAgreementsByUser(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTermId()).isEqualTo("1");
            assertThat(result.get(0).getTermTitle()).isEqualTo("이용약관");
            assertThat(result.get(0).getIsAgreed()).isTrue();
        }
    }

    @Nested
    @DisplayName("getPendingTermsByUser 테스트")
    class GetPendingTermsByUserTest {

        @Test
        @DisplayName("사용자가 동의하지 않은 약관 목록을 조회한다")
        void getPendingTermsByUser_success() {
            // given
            Object[] pendingTerm = createRawTermAgreement(1L, "이용약관", false);

            doReturn(createObjectArrayList(pendingTerm)).when(termsRepository).getPendingTermsByUserRaw(TEST_USER_ID);

            // when
            List<TermAgreementsByUserResponseDto> result = termsService.getPendingTermsByUser(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTermId()).isEqualTo("1");
            assertThat(result.get(0).getIsAgreed()).isFalse();
        }

        @Test
        @DisplayName("동의하지 않은 약관이 없으면 빈 목록을 반환한다")
        void getPendingTermsByUser_empty() {
            // given
            when(termsRepository.getPendingTermsByUserRaw(TEST_USER_ID)).thenReturn(Collections.emptyList());

            // when
            List<TermAgreementsByUserResponseDto> result = termsService.getPendingTermsByUser(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }
}
