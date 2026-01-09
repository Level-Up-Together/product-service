package io.pinkspider.leveluptogethermvp.userservice.terms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.RecentTermsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.TermAgreementsByUserResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.TermsRepository;
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

    // Interface mock implementation
    private RecentTermsResponseDto createMockRecentTerms(String termId, String termTitle, boolean isRequired) {
        return new RecentTermsResponseDto() {
            @Override
            public String getTermId() { return termId; }
            @Override
            public String getTermTitle() { return termTitle; }
            @Override
            public String getCode() { return "TERMS_001"; }
            @Override
            public String getType() { return "REQUIRED"; }
            @Override
            public boolean getIsRequired() { return isRequired; }
            @Override
            public String getVersionId() { return "1"; }
            @Override
            public String getVersion() { return "1.0"; }
            @Override
            public String getCreatedAt() { return "2024-01-01"; }
            @Override
            public String getContent() { return "약관 내용"; }
        };
    }

    private TermAgreementsByUserResponseDto createMockTermAgreement(String termId, String termTitle, boolean isAgreed) {
        return new TermAgreementsByUserResponseDto() {
            @Override
            public String getTermId() { return termId; }
            @Override
            public String getTermTitle() { return termTitle; }
            @Override
            public boolean getIsRequired() { return true; }
            @Override
            public String getLatestVersionId() { return "1"; }
            @Override
            public String getVersion() { return "1.0"; }
            @Override
            public boolean getIsAgreed() { return isAgreed; }
            @Override
            public String getAgreedAt() { return isAgreed ? "2024-01-01" : null; }
        };
    }

    @Nested
    @DisplayName("getRecentAllTerms 테스트")
    class GetRecentAllTermsTest {

        @Test
        @DisplayName("최근 약관 목록을 조회한다")
        void getRecentAllTerms_success() {
            // given
            RecentTermsResponseDto term1 = createMockRecentTerms("1", "이용약관", true);
            RecentTermsResponseDto term2 = createMockRecentTerms("2", "개인정보처리방침", true);

            when(termsRepository.getRecentAllTerms()).thenReturn(List.of(term1, term2));

            // when
            List<RecentTermsResponseDto> result = termsService.getRecentAllTerms();

            // then
            assertThat(result).hasSize(2);
            verify(termsRepository).getRecentAllTerms();
        }

        @Test
        @DisplayName("약관이 없으면 빈 목록을 반환한다")
        void getRecentAllTerms_empty() {
            // given
            when(termsRepository.getRecentAllTerms()).thenReturn(Collections.emptyList());

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
            TermAgreementsByUserResponseDto agreement = createMockTermAgreement("1", "이용약관", true);

            when(termsRepository.getTermAgreementsByUser(TEST_USER_ID)).thenReturn(List.of(agreement));

            // when
            List<TermAgreementsByUserResponseDto> result = termsService.getTermAgreementsByUser(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
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
            TermAgreementsByUserResponseDto pendingTerm = createMockTermAgreement("1", "이용약관", false);

            when(termsRepository.getPendingTermsByUser(TEST_USER_ID)).thenReturn(List.of(pendingTerm));

            // when
            List<TermAgreementsByUserResponseDto> result = termsService.getPendingTermsByUser(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsAgreed()).isFalse();
        }

        @Test
        @DisplayName("동의하지 않은 약관이 없으면 빈 목록을 반환한다")
        void getPendingTermsByUser_empty() {
            // given
            when(termsRepository.getPendingTermsByUser(TEST_USER_ID)).thenReturn(Collections.emptyList());

            // when
            List<TermAgreementsByUserResponseDto> result = termsService.getPendingTermsByUser(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }
}
