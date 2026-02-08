package io.pinkspider.leveluptogethermvp.userservice.terms.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.userservice.terms.domain.request.AgreementTermsByUserRequestDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.request.AgreementTermsByUserRequestDto.AgreementTerms;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.RecentTermsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.TermAgreementsByUserResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.UserService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.TermVersion;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserTermAgreement;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTermsServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private TermsService termsService;

    @Mock
    private TermVersionService termVersionService;

    @Mock
    private UserTermAgreementsService userTermAgreementsService;

    @InjectMocks
    private UserTermsService userTermsService;

    private static final String TEST_USER_ID = "test-user-123";

    private Users createTestUser(String userId, String nickname) {
        Users user = Users.builder()
            .nickname(nickname)
            .email(userId + "@test.com")
            .build();
        setId(user, userId);
        return user;
    }

    private TermVersion createTestTermVersion(Long id, String version) {
        TermVersion termVersion = TermVersion.builder()
            .version(version)
            .content("약관 내용")
            .build();
        setId(termVersion, id);
        return termVersion;
    }

    private UserTermAgreement createTestUserTermAgreement(Long id, Users user, TermVersion termVersion, boolean isAgreed) {
        UserTermAgreement agreement = UserTermAgreement.builder()
            .users(user)
            .termVersion(termVersion)
            .isAgreed(isAgreed)
            .build();
        setId(agreement, id);
        return agreement;
    }

    // Class-based DTO factory methods
    private RecentTermsResponseDto createMockRecentTerms(String termId, String termTitle, boolean isRequired) {
        return RecentTermsResponseDto.builder()
            .termId(termId)
            .termTitle(termTitle)
            .code("TERMS_001")
            .type("REQUIRED")
            .isRequired(isRequired)
            .versionId("1")
            .version("1.0")
            .createdAt("2024-01-01")
            .content("약관 내용")
            .build();
    }

    private TermAgreementsByUserResponseDto createMockTermAgreement(String termId, String termTitle, boolean isAgreed) {
        return TermAgreementsByUserResponseDto.builder()
            .termId(termId)
            .termTitle(termTitle)
            .isRequired(true)
            .latestVersionId("1")
            .version("1.0")
            .isAgreed(isAgreed)
            .agreedAt(isAgreed ? "2024-01-01" : null)
            .build();
    }

    @Nested
    @DisplayName("getRecentAllTerms 테스트")
    class GetRecentAllTermsTest {

        @Test
        @DisplayName("최근 약관 목록을 조회한다")
        void getRecentAllTerms_success() {
            // given
            RecentTermsResponseDto term1 = createMockRecentTerms("1", "이용약관", true);

            when(termsService.getRecentAllTerms()).thenReturn(List.of(term1));

            // when
            List<RecentTermsResponseDto> result = userTermsService.getRecentAllTerms();

            // then
            assertThat(result).hasSize(1);
            verify(termsService).getRecentAllTerms();
        }

        @Test
        @DisplayName("약관이 없으면 빈 목록을 반환한다")
        void getRecentAllTerms_empty() {
            // given
            when(termsService.getRecentAllTerms()).thenReturn(Collections.emptyList());

            // when
            List<RecentTermsResponseDto> result = userTermsService.getRecentAllTerms();

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

            when(termsService.getTermAgreementsByUser(TEST_USER_ID)).thenReturn(List.of(agreement));

            // when
            List<TermAgreementsByUserResponseDto> result = userTermsService.getTermAgreementsByUser(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            verify(termsService).getTermAgreementsByUser(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("getPendingTermsByUser 테스트")
    class GetPendingTermsByUserTest {

        @Test
        @DisplayName("동의하지 않은 약관 목록을 조회한다")
        void getPendingTermsByUser_success() {
            // given
            TermAgreementsByUserResponseDto pendingTerm = createMockTermAgreement("1", "이용약관", false);

            when(termsService.getPendingTermsByUser(TEST_USER_ID)).thenReturn(List.of(pendingTerm));

            // when
            List<TermAgreementsByUserResponseDto> result = userTermsService.getPendingTermsByUser(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsAgreed()).isFalse();
            verify(termsService).getPendingTermsByUser(TEST_USER_ID);
        }

        @Test
        @DisplayName("모든 약관에 동의했으면 빈 목록을 반환한다")
        void getPendingTermsByUser_allAgreed() {
            // given
            when(termsService.getPendingTermsByUser(TEST_USER_ID)).thenReturn(Collections.emptyList());

            // when
            List<TermAgreementsByUserResponseDto> result = userTermsService.getPendingTermsByUser(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("agreementTermsByUser 테스트")
    class AgreementTermsByUserTest {

        @Test
        @DisplayName("기존 동의 기록이 있으면 업데이트한다")
        void agreementTermsByUser_update() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스트유저");
            TermVersion termVersion = createTestTermVersion(1L, "1.0");
            UserTermAgreement existingAgreement = createTestUserTermAgreement(1L, user, termVersion, false);

            AgreementTerms agreementTerms = AgreementTerms.builder()
                .termVersionId(1L)
                .isAgreed(true)
                .build();

            AgreementTermsByUserRequestDto requestDto = AgreementTermsByUserRequestDto.builder()
                .AgreementTermsList(List.of(agreementTerms))
                .build();

            when(userTermAgreementsService.findAllByUserIdAndTermVersionId(TEST_USER_ID, 1L))
                .thenReturn(existingAgreement);

            // when
            userTermsService.agreementTermsByUser(TEST_USER_ID, requestDto);

            // then
            assertThat(existingAgreement.getIsAgreed()).isTrue();
            verify(userTermAgreementsService).save(existingAgreement);
            verify(userService, never()).findByUserId(any());
            verify(termVersionService, never()).findById(any());
        }

        @Test
        @DisplayName("기존 동의 기록이 없으면 새로 생성한다")
        void agreementTermsByUser_create() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스트유저");
            TermVersion termVersion = createTestTermVersion(1L, "1.0");

            AgreementTerms agreementTerms = AgreementTerms.builder()
                .termVersionId(1L)
                .isAgreed(true)
                .build();

            AgreementTermsByUserRequestDto requestDto = AgreementTermsByUserRequestDto.builder()
                .AgreementTermsList(List.of(agreementTerms))
                .build();

            when(userTermAgreementsService.findAllByUserIdAndTermVersionId(TEST_USER_ID, 1L))
                .thenReturn(null);
            when(userService.findByUserId(TEST_USER_ID)).thenReturn(user);
            when(termVersionService.findById(1L)).thenReturn(termVersion);

            // when
            userTermsService.agreementTermsByUser(TEST_USER_ID, requestDto);

            // then
            ArgumentCaptor<UserTermAgreement> captor = ArgumentCaptor.forClass(UserTermAgreement.class);
            verify(userTermAgreementsService).save(captor.capture());

            UserTermAgreement savedAgreement = captor.getValue();
            assertThat(savedAgreement.getUsers()).isEqualTo(user);
            assertThat(savedAgreement.getTermVersion()).isEqualTo(termVersion);
            assertThat(savedAgreement.getIsAgreed()).isTrue();
        }

        @Test
        @DisplayName("여러 약관에 대해 동의를 처리한다")
        void agreementTermsByUser_multiple() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스트유저");
            TermVersion termVersion1 = createTestTermVersion(1L, "1.0");
            TermVersion termVersion2 = createTestTermVersion(2L, "1.0");
            UserTermAgreement existingAgreement = createTestUserTermAgreement(1L, user, termVersion1, false);

            AgreementTerms agreementTerms1 = AgreementTerms.builder()
                .termVersionId(1L)
                .isAgreed(true)
                .build();

            AgreementTerms agreementTerms2 = AgreementTerms.builder()
                .termVersionId(2L)
                .isAgreed(true)
                .build();

            AgreementTermsByUserRequestDto requestDto = AgreementTermsByUserRequestDto.builder()
                .AgreementTermsList(List.of(agreementTerms1, agreementTerms2))
                .build();

            when(userTermAgreementsService.findAllByUserIdAndTermVersionId(TEST_USER_ID, 1L))
                .thenReturn(existingAgreement);
            when(userTermAgreementsService.findAllByUserIdAndTermVersionId(TEST_USER_ID, 2L))
                .thenReturn(null);
            when(userService.findByUserId(TEST_USER_ID)).thenReturn(user);
            when(termVersionService.findById(2L)).thenReturn(termVersion2);

            // when
            userTermsService.agreementTermsByUser(TEST_USER_ID, requestDto);

            // then
            verify(userTermAgreementsService, times(2)).save(any(UserTermAgreement.class));
            assertThat(existingAgreement.getIsAgreed()).isTrue();
        }

        @Test
        @DisplayName("동의 거부를 처리한다")
        void agreementTermsByUser_disagree() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스트유저");
            TermVersion termVersion = createTestTermVersion(1L, "1.0");
            UserTermAgreement existingAgreement = createTestUserTermAgreement(1L, user, termVersion, true);

            AgreementTerms agreementTerms = AgreementTerms.builder()
                .termVersionId(1L)
                .isAgreed(false)
                .build();

            AgreementTermsByUserRequestDto requestDto = AgreementTermsByUserRequestDto.builder()
                .AgreementTermsList(List.of(agreementTerms))
                .build();

            when(userTermAgreementsService.findAllByUserIdAndTermVersionId(TEST_USER_ID, 1L))
                .thenReturn(existingAgreement);

            // when
            userTermsService.agreementTermsByUser(TEST_USER_ID, requestDto);

            // then
            assertThat(existingAgreement.getIsAgreed()).isFalse();
            verify(userTermAgreementsService).save(existingAgreement);
        }
    }
}
