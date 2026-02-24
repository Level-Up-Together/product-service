package io.pinkspider.leveluptogethermvp.userservice.terms.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermVersionAdminRequest;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermVersionAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermsAdminPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermsAdminRequest;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermsAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.UserAgreementSummaryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.UserTermAgreementAdminPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.UserTermAgreementAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.TermVersionRepository;
import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.TermsRepository;
import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.UserTermAgreementsRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Term;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.TermVersion;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserTermAgreement;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import java.time.LocalDateTime;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TermsAdminInternalServiceTest {

    @Mock
    private TermsRepository termsRepository;

    @Mock
    private TermVersionRepository termVersionRepository;

    @Mock
    private UserTermAgreementsRepository userTermAgreementsRepository;

    @InjectMocks
    private TermsAdminInternalService termsAdminInternalService;

    private static final String TEST_USER_ID = "test-user-123";

    private Term createTestTerm(Long id, String code, String title) {
        Term term = Term.builder()
            .code(code)
            .title(title)
            .description("테스트 약관 설명")
            .type("REQUIRED")
            .isRequired(true)
            .build();
        setId(term, id);
        return term;
    }

    private TermVersion createTestTermVersion(Long id, Term term, String version) {
        TermVersion termVersion = TermVersion.builder()
            .terms(term)
            .version(version)
            .content("약관 내용")
            .build();
        setId(termVersion, id);
        return termVersion;
    }

    private Users createTestUser(String userId) {
        Users user = Users.builder()
            .nickname("테스트유저")
            .email(userId + "@test.com")
            .build();
        setId(user, userId);
        return user;
    }

    // ==================== Terms CRUD ====================

    @Nested
    @DisplayName("getAllTerms 테스트")
    class GetAllTermsTest {

        @Test
        @DisplayName("전체 약관 목록을 ID 내림차순으로 조회한다")
        void getAllTerms_success() {
            // given
            Term term1 = createTestTerm(2L, "TERMS_002", "개인정보처리방침");
            Term term2 = createTestTerm(1L, "TERMS_001", "이용약관");

            when(termsRepository.findAllByOrderByIdDesc()).thenReturn(List.of(term1, term2));

            // when
            List<TermsAdminResponse> result = termsAdminInternalService.getAllTerms();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(2L);
            assertThat(result.get(0).code()).isEqualTo("TERMS_002");
            assertThat(result.get(1).id()).isEqualTo(1L);
            verify(termsRepository).findAllByOrderByIdDesc();
        }

        @Test
        @DisplayName("약관이 없으면 빈 목록을 반환한다")
        void getAllTerms_empty() {
            // given
            when(termsRepository.findAllByOrderByIdDesc()).thenReturn(Collections.emptyList());

            // when
            List<TermsAdminResponse> result = termsAdminInternalService.getAllTerms();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchTerms 테스트")
    class SearchTermsTest {

        @Test
        @DisplayName("키워드로 약관을 페이징 조회한다")
        void searchTerms_success() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");
            Page<Term> page = new PageImpl<>(List.of(term), PageRequest.of(0, 10), 1);
            Pageable pageable = PageRequest.of(0, 10);

            when(termsRepository.searchByKeyword(eq("이용약관"), eq(pageable))).thenReturn(page);

            // when
            TermsAdminPageResponse result = termsAdminInternalService.searchTerms("이용약관", pageable);

            // then
            assertThat(result).isNotNull();
            verify(termsRepository).searchByKeyword("이용약관", pageable);
        }
    }

    @Nested
    @DisplayName("getTerms 테스트")
    class GetTermsTest {

        @Test
        @DisplayName("ID로 약관(버전 포함)을 조회한다")
        void getTerms_success() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");

            when(termsRepository.findByIdWithVersions(1L)).thenReturn(Optional.of(term));

            // when
            TermsAdminResponse result = termsAdminInternalService.getTerms(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.code()).isEqualTo("TERMS_001");
        }

        @Test
        @DisplayName("존재하지 않는 약관 조회 시 예외를 발생시킨다")
        void getTerms_notFound_throwsException() {
            // given
            when(termsRepository.findByIdWithVersions(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.getTerms(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getTermsByCode 테스트")
    class GetTermsByCodeTest {

        @Test
        @DisplayName("코드로 약관을 조회한다")
        void getTermsByCode_success() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");

            when(termsRepository.findByCode("TERMS_001")).thenReturn(Optional.of(term));

            // when
            TermsAdminResponse result = termsAdminInternalService.getTermsByCode("TERMS_001");

            // then
            assertThat(result).isNotNull();
            assertThat(result.code()).isEqualTo("TERMS_001");
        }

        @Test
        @DisplayName("존재하지 않는 코드로 조회 시 예외를 발생시킨다")
        void getTermsByCode_notFound_throwsException() {
            // given
            when(termsRepository.findByCode("INVALID")).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.getTermsByCode("INVALID"))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getRequiredTerms 테스트")
    class GetRequiredTermsTest {

        @Test
        @DisplayName("필수 약관 목록을 조회한다")
        void getRequiredTerms_success() {
            // given
            Term term1 = createTestTerm(1L, "TERMS_001", "이용약관");
            Term term2 = createTestTerm(2L, "TERMS_002", "개인정보처리방침");

            when(termsRepository.findByIsRequiredTrueOrderByIdAsc()).thenReturn(List.of(term1, term2));

            // when
            List<TermsAdminResponse> result = termsAdminInternalService.getRequiredTerms();

            // then
            assertThat(result).hasSize(2);
            verify(termsRepository).findByIsRequiredTrueOrderByIdAsc();
        }
    }

    @Nested
    @DisplayName("getTermsByType 테스트")
    class GetTermsByTypeTest {

        @Test
        @DisplayName("타입으로 약관 목록을 조회한다")
        void getTermsByType_success() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");

            when(termsRepository.findByTypeOrderByIdAsc("REQUIRED")).thenReturn(List.of(term));

            // when
            List<TermsAdminResponse> result = termsAdminInternalService.getTermsByType("REQUIRED");

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).type()).isEqualTo("REQUIRED");
            verify(termsRepository).findByTypeOrderByIdAsc("REQUIRED");
        }
    }

    @Nested
    @DisplayName("getAllTermTypes 테스트")
    class GetAllTermTypesTest {

        @Test
        @DisplayName("모든 약관 타입을 조회한다")
        void getAllTermTypes_success() {
            // given
            when(termsRepository.findAllTypes()).thenReturn(List.of("REQUIRED", "OPTIONAL"));

            // when
            List<String> result = termsAdminInternalService.getAllTermTypes();

            // then
            assertThat(result).containsExactly("REQUIRED", "OPTIONAL");
            verify(termsRepository).findAllTypes();
        }
    }

    @Nested
    @DisplayName("createTerms 테스트")
    class CreateTermsTest {

        @Test
        @DisplayName("새 약관을 생성한다")
        void createTerms_success() {
            // given
            TermsAdminRequest request = new TermsAdminRequest(
                "TERMS_NEW", "새로운 약관", "약관 설명", "REQUIRED", true
            );
            Term savedTerm = createTestTerm(10L, "TERMS_NEW", "새로운 약관");

            when(termsRepository.existsByCode("TERMS_NEW")).thenReturn(false);
            when(termsRepository.save(any(Term.class))).thenReturn(savedTerm);

            // when
            TermsAdminResponse result = termsAdminInternalService.createTerms(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.code()).isEqualTo("TERMS_NEW");
            verify(termsRepository).existsByCode("TERMS_NEW");
            verify(termsRepository).save(any(Term.class));
        }

        @Test
        @DisplayName("이미 존재하는 코드로 약관 생성 시 예외를 발생시킨다")
        void createTerms_duplicateCode_throwsException() {
            // given
            TermsAdminRequest request = new TermsAdminRequest(
                "TERMS_001", "중복 약관", "설명", "REQUIRED", true
            );

            when(termsRepository.existsByCode("TERMS_001")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.createTerms(request))
                .isInstanceOf(CustomException.class);
            verify(termsRepository).existsByCode("TERMS_001");
        }

        @Test
        @DisplayName("isRequired가 null이면 false로 기본 설정된다")
        void createTerms_nullIsRequired_defaultFalse() {
            // given
            TermsAdminRequest request = new TermsAdminRequest(
                "TERMS_OPT", "선택 약관", "설명", "OPTIONAL", null
            );
            Term savedTerm = createTestTerm(11L, "TERMS_OPT", "선택 약관");

            when(termsRepository.existsByCode("TERMS_OPT")).thenReturn(false);
            when(termsRepository.save(any(Term.class))).thenReturn(savedTerm);

            // when
            TermsAdminResponse result = termsAdminInternalService.createTerms(request);

            // then
            assertThat(result).isNotNull();
            verify(termsRepository).save(any(Term.class));
        }
    }

    @Nested
    @DisplayName("updateTerms 테스트")
    class UpdateTermsTest {

        @Test
        @DisplayName("약관을 수정한다")
        void updateTerms_success() {
            // given
            Term existingTerm = createTestTerm(1L, "TERMS_001", "이용약관");
            TermsAdminRequest request = new TermsAdminRequest(
                "TERMS_001", "이용약관 수정", "수정된 설명", "REQUIRED", true
            );

            when(termsRepository.findById(1L)).thenReturn(Optional.of(existingTerm));
            when(termsRepository.save(any(Term.class))).thenReturn(existingTerm);

            // when
            TermsAdminResponse result = termsAdminInternalService.updateTerms(1L, request);

            // then
            assertThat(result).isNotNull();
            verify(termsRepository).findById(1L);
            verify(termsRepository).save(existingTerm);
        }

        @Test
        @DisplayName("코드 변경 시 중복 코드가 있으면 예외를 발생시킨다")
        void updateTerms_duplicateNewCode_throwsException() {
            // given
            Term existingTerm = createTestTerm(1L, "TERMS_001", "이용약관");
            TermsAdminRequest request = new TermsAdminRequest(
                "TERMS_002", "이용약관 수정", "설명", "REQUIRED", true
            );

            when(termsRepository.findById(1L)).thenReturn(Optional.of(existingTerm));
            when(termsRepository.existsByCode("TERMS_002")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.updateTerms(1L, request))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("존재하지 않는 약관 수정 시 예외를 발생시킨다")
        void updateTerms_notFound_throwsException() {
            // given
            TermsAdminRequest request = new TermsAdminRequest(
                "TERMS_001", "약관", "설명", "REQUIRED", true
            );

            when(termsRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.updateTerms(999L, request))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("deleteTerms 테스트")
    class DeleteTermsTest {

        @Test
        @DisplayName("약관을 삭제한다")
        void deleteTerms_success() {
            // given
            when(termsRepository.existsById(1L)).thenReturn(true);

            // when
            termsAdminInternalService.deleteTerms(1L);

            // then
            verify(termsRepository).existsById(1L);
            verify(termsRepository).deleteById(1L);
        }

        @Test
        @DisplayName("존재하지 않는 약관 삭제 시 예외를 발생시킨다")
        void deleteTerms_notFound_throwsException() {
            // given
            when(termsRepository.existsById(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.deleteTerms(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    // ==================== Term Version CRUD ====================

    @Nested
    @DisplayName("getTermVersions 테스트")
    class GetTermVersionsTest {

        @Test
        @DisplayName("약관의 버전 목록을 조회한다")
        void getTermVersions_success() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");
            TermVersion v1 = createTestTermVersion(2L, term, "2.0");
            TermVersion v2 = createTestTermVersion(1L, term, "1.0");

            when(termsRepository.existsById(1L)).thenReturn(true);
            when(termVersionRepository.findByTermsIdOrderByIdDesc(1L)).thenReturn(List.of(v1, v2));

            // when
            List<TermVersionAdminResponse> result = termsAdminInternalService.getTermVersions(1L);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(2L);
            assertThat(result.get(0).version()).isEqualTo("2.0");
        }

        @Test
        @DisplayName("존재하지 않는 약관의 버전 조회 시 예외를 발생시킨다")
        void getTermVersions_termsNotFound_throwsException() {
            // given
            when(termsRepository.existsById(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.getTermVersions(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getTermVersion 테스트")
    class GetTermVersionTest {

        @Test
        @DisplayName("버전 ID로 약관 버전을 조회한다")
        void getTermVersion_success() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");
            TermVersion version = createTestTermVersion(1L, term, "1.0");

            when(termVersionRepository.findByIdWithTerms(1L)).thenReturn(Optional.of(version));

            // when
            TermVersionAdminResponse result = termsAdminInternalService.getTermVersion(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.version()).isEqualTo("1.0");
            assertThat(result.termsId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 버전 조회 시 예외를 발생시킨다")
        void getTermVersion_notFound_throwsException() {
            // given
            when(termVersionRepository.findByIdWithTerms(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.getTermVersion(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getLatestTermVersion 테스트")
    class GetLatestTermVersionTest {

        @Test
        @DisplayName("가장 최신 약관 버전을 조회한다")
        void getLatestTermVersion_success() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");
            TermVersion latestVersion = createTestTermVersion(3L, term, "3.0");

            when(termVersionRepository.findTopByTermsIdOrderByIdDesc(1L))
                .thenReturn(Optional.of(latestVersion));

            // when
            TermVersionAdminResponse result = termsAdminInternalService.getLatestTermVersion(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(3L);
            assertThat(result.version()).isEqualTo("3.0");
        }

        @Test
        @DisplayName("버전이 없으면 예외를 발생시킨다")
        void getLatestTermVersion_notFound_throwsException() {
            // given
            when(termVersionRepository.findTopByTermsIdOrderByIdDesc(1L))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.getLatestTermVersion(1L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("createTermVersion 테스트")
    class CreateTermVersionTest {

        @Test
        @DisplayName("새 약관 버전을 생성한다")
        void createTermVersion_success() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");
            TermVersionAdminRequest request = new TermVersionAdminRequest("2.0", "새로운 약관 내용");
            TermVersion savedVersion = createTestTermVersion(5L, term, "2.0");

            when(termsRepository.findById(1L)).thenReturn(Optional.of(term));
            when(termVersionRepository.existsByTermsIdAndVersion(1L, "2.0")).thenReturn(false);
            when(termVersionRepository.save(any(TermVersion.class))).thenReturn(savedVersion);

            // when
            TermVersionAdminResponse result = termsAdminInternalService.createTermVersion(1L, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.version()).isEqualTo("2.0");
            verify(termVersionRepository).save(any(TermVersion.class));
        }

        @Test
        @DisplayName("이미 존재하는 버전으로 생성 시 예외를 발생시킨다")
        void createTermVersion_duplicateVersion_throwsException() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");
            TermVersionAdminRequest request = new TermVersionAdminRequest("1.0", "약관 내용");

            when(termsRepository.findById(1L)).thenReturn(Optional.of(term));
            when(termVersionRepository.existsByTermsIdAndVersion(1L, "1.0")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.createTermVersion(1L, request))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("존재하지 않는 약관에 버전 생성 시 예외를 발생시킨다")
        void createTermVersion_termsNotFound_throwsException() {
            // given
            TermVersionAdminRequest request = new TermVersionAdminRequest("1.0", "약관 내용");

            when(termsRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.createTermVersion(999L, request))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("updateTermVersion 테스트")
    class UpdateTermVersionTest {

        @Test
        @DisplayName("약관 버전을 수정한다")
        void updateTermVersion_success() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");
            TermVersion existingVersion = createTestTermVersion(1L, term, "1.0");
            TermVersionAdminRequest request = new TermVersionAdminRequest("1.0", "수정된 약관 내용");

            when(termVersionRepository.findByIdWithTerms(1L)).thenReturn(Optional.of(existingVersion));
            when(termVersionRepository.save(any(TermVersion.class))).thenReturn(existingVersion);

            // when
            TermVersionAdminResponse result = termsAdminInternalService.updateTermVersion(1L, request);

            // then
            assertThat(result).isNotNull();
            verify(termVersionRepository).save(existingVersion);
        }

        @Test
        @DisplayName("버전 번호 변경 시 중복 버전이 있으면 예외를 발생시킨다")
        void updateTermVersion_duplicateNewVersion_throwsException() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");
            TermVersion existingVersion = createTestTermVersion(1L, term, "1.0");
            TermVersionAdminRequest request = new TermVersionAdminRequest("2.0", "약관 내용");

            when(termVersionRepository.findByIdWithTerms(1L)).thenReturn(Optional.of(existingVersion));
            when(termVersionRepository.existsByTermsIdAndVersion(1L, "2.0")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.updateTermVersion(1L, request))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("존재하지 않는 버전 수정 시 예외를 발생시킨다")
        void updateTermVersion_notFound_throwsException() {
            // given
            TermVersionAdminRequest request = new TermVersionAdminRequest("1.0", "약관 내용");

            when(termVersionRepository.findByIdWithTerms(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.updateTermVersion(999L, request))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("deleteTermVersion 테스트")
    class DeleteTermVersionTest {

        @Test
        @DisplayName("약관 버전을 삭제한다")
        void deleteTermVersion_success() {
            // given
            when(termVersionRepository.existsById(1L)).thenReturn(true);

            // when
            termsAdminInternalService.deleteTermVersion(1L);

            // then
            verify(termVersionRepository).existsById(1L);
            verify(termVersionRepository).deleteById(1L);
        }

        @Test
        @DisplayName("존재하지 않는 버전 삭제 시 예외를 발생시킨다")
        void deleteTermVersion_notFound_throwsException() {
            // given
            when(termVersionRepository.existsById(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.deleteTermVersion(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    // ==================== User Term Agreements ====================

    @Nested
    @DisplayName("getUserAgreements 테스트")
    class GetUserAgreementsTest {

        @Test
        @DisplayName("사용자의 약관 동의 목록을 조회한다")
        void getUserAgreements_success() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");
            TermVersion termVersion = createTestTermVersion(1L, term, "1.0");
            Users user = createTestUser(TEST_USER_ID);
            UserTermAgreement agreement = UserTermAgreement.builder()
                .users(user)
                .termVersion(termVersion)
                .isAgreed(true)
                .build();
            setId(agreement, 1L);

            when(userTermAgreementsRepository.findByUserIdWithTerms(TEST_USER_ID))
                .thenReturn(List.of(agreement));

            // when
            List<UserTermAgreementAdminResponse> result =
                termsAdminInternalService.getUserAgreements(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).userId()).isEqualTo(TEST_USER_ID);
            assertThat(result.get(0).isAgreed()).isTrue();
        }
    }

    @Nested
    @DisplayName("getUserAgreementsByTerms 테스트")
    class GetUserAgreementsByTermsTest {

        @Test
        @DisplayName("사용자의 특정 약관에 대한 동의 목록을 조회한다")
        void getUserAgreementsByTerms_success() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");
            TermVersion termVersion = createTestTermVersion(1L, term, "1.0");
            Users user = createTestUser(TEST_USER_ID);
            UserTermAgreement agreement = UserTermAgreement.builder()
                .users(user)
                .termVersion(termVersion)
                .isAgreed(true)
                .build();
            setId(agreement, 1L);

            when(termsRepository.existsById(1L)).thenReturn(true);
            when(userTermAgreementsRepository.findByUserIdAndTermsId(TEST_USER_ID, 1L))
                .thenReturn(List.of(agreement));

            // when
            List<UserTermAgreementAdminResponse> result =
                termsAdminInternalService.getUserAgreementsByTerms(TEST_USER_ID, 1L);

            // then
            assertThat(result).hasSize(1);
            verify(userTermAgreementsRepository).findByUserIdAndTermsId(TEST_USER_ID, 1L);
        }

        @Test
        @DisplayName("존재하지 않는 약관에 대한 동의 조회 시 예외를 발생시킨다")
        void getUserAgreementsByTerms_termsNotFound_throwsException() {
            // given
            when(termsRepository.existsById(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(
                () -> termsAdminInternalService.getUserAgreementsByTerms(TEST_USER_ID, 999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getAgreementCountByTermVersion 테스트")
    class GetAgreementCountByTermVersionTest {

        @Test
        @DisplayName("약관 버전별 동의 수를 조회한다")
        void getAgreementCountByTermVersion_success() {
            // given
            when(termVersionRepository.existsById(1L)).thenReturn(true);
            when(userTermAgreementsRepository.countAgreedByTermVersionId(1L)).thenReturn(100L);

            // when
            Long result = termsAdminInternalService.getAgreementCountByTermVersion(1L);

            // then
            assertThat(result).isEqualTo(100L);
        }

        @Test
        @DisplayName("존재하지 않는 버전의 동의 수 조회 시 예외를 발생시킨다")
        void getAgreementCountByTermVersion_notFound_throwsException() {
            // given
            when(termVersionRepository.existsById(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(
                () -> termsAdminInternalService.getAgreementCountByTermVersion(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getAgreementCountByTerms 테스트")
    class GetAgreementCountByTermsTest {

        @Test
        @DisplayName("약관별 동의한 고유 사용자 수를 조회한다")
        void getAgreementCountByTerms_success() {
            // given
            when(termsRepository.existsById(1L)).thenReturn(true);
            when(userTermAgreementsRepository.countDistinctUsersByTermsIdAndAgreed(1L)).thenReturn(50L);

            // when
            Long result = termsAdminInternalService.getAgreementCountByTerms(1L);

            // then
            assertThat(result).isEqualTo(50L);
        }

        @Test
        @DisplayName("존재하지 않는 약관의 동의 수 조회 시 예외를 발생시킨다")
        void getAgreementCountByTerms_notFound_throwsException() {
            // given
            when(termsRepository.existsById(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> termsAdminInternalService.getAgreementCountByTerms(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("searchAllAgreements 테스트")
    class SearchAllAgreementsTest {

        @Test
        @DisplayName("조건으로 약관 동의 목록을 페이징 조회한다")
        void searchAllAgreements_success() {
            // given
            Term term = createTestTerm(1L, "TERMS_001", "이용약관");
            TermVersion termVersion = createTestTermVersion(1L, term, "1.0");
            Users user = createTestUser(TEST_USER_ID);
            UserTermAgreement agreement = UserTermAgreement.builder()
                .users(user)
                .termVersion(termVersion)
                .isAgreed(true)
                .build();
            setId(agreement, 1L);

            Pageable pageable = PageRequest.of(0, 10);
            Page<UserTermAgreement> page = new PageImpl<>(List.of(agreement), pageable, 1);

            when(userTermAgreementsRepository.searchAgreementsWithFetch(
                eq(TEST_USER_ID), eq(1L), eq(true), eq(pageable)))
                .thenReturn(page);

            // when
            UserTermAgreementAdminPageResponse result =
                termsAdminInternalService.searchAllAgreements(TEST_USER_ID, 1L, true, pageable);

            // then
            assertThat(result).isNotNull();
            verify(userTermAgreementsRepository).searchAgreementsWithFetch(
                TEST_USER_ID, 1L, true, pageable);
        }
    }

    @Nested
    @DisplayName("getUserAgreementSummaries 테스트")
    class GetUserAgreementSummariesTest {

        @Test
        @DisplayName("사용자 약관 동의 요약을 페이징 조회한다")
        void getUserAgreementSummaries_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<String> userIdPage = new PageImpl<>(List.of(TEST_USER_ID), pageable, 1);

            when(userTermAgreementsRepository.findDistinctUserIds(anyString(), eq(pageable)))
                .thenReturn(userIdPage);
            when(userTermAgreementsRepository.countByUsersId(TEST_USER_ID)).thenReturn(5L);
            when(userTermAgreementsRepository.countAgreedByUsersId(TEST_USER_ID)).thenReturn(3L);
            when(userTermAgreementsRepository.countRequiredTermsByUsersId(TEST_USER_ID)).thenReturn(2L);
            when(userTermAgreementsRepository.countRequiredAgreedByUsersId(TEST_USER_ID)).thenReturn(2L);
            when(userTermAgreementsRepository.findLastAgreedAtByUsersId(TEST_USER_ID))
                .thenReturn(LocalDateTime.now());

            // when
            UserAgreementSummaryAdminPageResponse result =
                termsAdminInternalService.getUserAgreementSummaries("test", pageable);

            // then
            assertThat(result).isNotNull();
            verify(userTermAgreementsRepository).findDistinctUserIds("test", pageable);
            verify(userTermAgreementsRepository).countByUsersId(TEST_USER_ID);
            verify(userTermAgreementsRepository).countAgreedByUsersId(TEST_USER_ID);
        }
    }
}
