package io.pinkspider.leveluptogethermvp.userservice.terms.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermVersionAdminRequest;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermVersionAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermsAdminPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermsAdminRequest;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.TermsAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.UserAgreementSummaryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.UserAgreementSummaryAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.UserTermAgreementAdminPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin.UserTermAgreementAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.TermVersionRepository;
import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.TermsRepository;
import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.UserTermAgreementsRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Term;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.TermVersion;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "userTransactionManager")
public class TermsAdminInternalService {

    private final TermsRepository termsRepository;
    private final TermVersionRepository termVersionRepository;
    private final UserTermAgreementsRepository userTermAgreementsRepository;

    // ==================== Terms CRUD ====================

    public List<TermsAdminResponse> getAllTerms() {
        return termsRepository.findAllByOrderByIdDesc().stream()
            .map(TermsAdminResponse::fromSimple)
            .collect(Collectors.toList());
    }

    public TermsAdminPageResponse searchTerms(String keyword, Pageable pageable) {
        Page<TermsAdminResponse> page = termsRepository.searchByKeyword(keyword, pageable)
            .map(TermsAdminResponse::fromSimple);
        return TermsAdminPageResponse.from(page);
    }

    public TermsAdminResponse getTerms(Long id) {
        Term term = termsRepository.findByIdWithVersions(id)
            .orElseThrow(() -> new CustomException("404", "약관을 찾을 수 없습니다."));
        return TermsAdminResponse.from(term);
    }

    public TermsAdminResponse getTermsByCode(String code) {
        Term term = termsRepository.findByCode(code)
            .orElseThrow(() -> new CustomException("404", "약관을 찾을 수 없습니다."));
        return TermsAdminResponse.fromSimple(term);
    }

    public List<TermsAdminResponse> getRequiredTerms() {
        return termsRepository.findByIsRequiredTrueOrderByIdAsc().stream()
            .map(TermsAdminResponse::fromSimple)
            .collect(Collectors.toList());
    }

    public List<TermsAdminResponse> getTermsByType(String type) {
        return termsRepository.findByTypeOrderByIdAsc(type).stream()
            .map(TermsAdminResponse::fromSimple)
            .collect(Collectors.toList());
    }

    public List<String> getAllTermTypes() {
        return termsRepository.findAllTypes();
    }

    @Transactional(transactionManager = "userTransactionManager")
    public TermsAdminResponse createTerms(TermsAdminRequest request) {
        if (termsRepository.existsByCode(request.code())) {
            throw new CustomException("400", "이미 존재하는 약관 코드입니다.");
        }

        Term term = Term.builder()
            .code(request.code())
            .title(request.title())
            .description(request.description())
            .type(request.type())
            .isRequired(request.isRequired() != null ? request.isRequired() : false)
            .build();

        Term saved = termsRepository.save(term);
        log.info("약관 생성: id={}, code={}", saved.getId(), saved.getCode());
        return TermsAdminResponse.fromSimple(saved);
    }

    @Transactional(transactionManager = "userTransactionManager")
    public TermsAdminResponse updateTerms(Long id, TermsAdminRequest request) {
        Term term = termsRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "약관을 찾을 수 없습니다."));

        if (!term.getCode().equals(request.code())
            && termsRepository.existsByCode(request.code())) {
            throw new CustomException("400", "이미 존재하는 약관 코드입니다.");
        }

        term.setCode(request.code());
        term.setTitle(request.title());
        term.setDescription(request.description());
        term.setType(request.type());
        term.setIsRequired(request.isRequired());

        Term saved = termsRepository.save(term);
        log.info("약관 수정: id={}, code={}", saved.getId(), saved.getCode());
        return TermsAdminResponse.fromSimple(saved);
    }

    @Transactional(transactionManager = "userTransactionManager")
    public void deleteTerms(Long id) {
        if (!termsRepository.existsById(id)) {
            throw new CustomException("404", "약관을 찾을 수 없습니다.");
        }
        termsRepository.deleteById(id);
        log.info("약관 삭제: id={}", id);
    }

    // ==================== Term Version CRUD ====================

    public List<TermVersionAdminResponse> getTermVersions(Long termsId) {
        if (!termsRepository.existsById(termsId)) {
            throw new CustomException("404", "약관을 찾을 수 없습니다.");
        }
        return termVersionRepository.findByTermsIdOrderByIdDesc(termsId).stream()
            .map(TermVersionAdminResponse::from)
            .collect(Collectors.toList());
    }

    public TermVersionAdminResponse getTermVersion(Long versionId) {
        TermVersion version = termVersionRepository.findByIdWithTerms(versionId)
            .orElseThrow(() -> new CustomException("404", "약관 버전을 찾을 수 없습니다."));
        return TermVersionAdminResponse.from(version);
    }

    public TermVersionAdminResponse getLatestTermVersion(Long termsId) {
        TermVersion version = termVersionRepository.findTopByTermsIdOrderByIdDesc(termsId)
            .orElseThrow(() -> new CustomException("404", "약관 버전을 찾을 수 없습니다."));
        return TermVersionAdminResponse.from(version);
    }

    @Transactional(transactionManager = "userTransactionManager")
    public TermVersionAdminResponse createTermVersion(Long termsId, TermVersionAdminRequest request) {
        Term term = termsRepository.findById(termsId)
            .orElseThrow(() -> new CustomException("404", "약관을 찾을 수 없습니다."));

        if (termVersionRepository.existsByTermsIdAndVersion(termsId, request.version())) {
            throw new CustomException("400", "이미 존재하는 버전입니다.");
        }

        TermVersion version = TermVersion.builder()
            .terms(term)
            .version(request.version())
            .content(request.content())
            .build();

        TermVersion saved = termVersionRepository.save(version);
        log.info("약관 버전 생성: termsId={}, versionId={}, version={}", termsId, saved.getId(), saved.getVersion());
        return TermVersionAdminResponse.from(saved);
    }

    @Transactional(transactionManager = "userTransactionManager")
    public TermVersionAdminResponse updateTermVersion(Long versionId, TermVersionAdminRequest request) {
        TermVersion version = termVersionRepository.findByIdWithTerms(versionId)
            .orElseThrow(() -> new CustomException("404", "약관 버전을 찾을 수 없습니다."));

        if (!version.getVersion().equals(request.version())
            && termVersionRepository.existsByTermsIdAndVersion(version.getTerms().getId(), request.version())) {
            throw new CustomException("400", "이미 존재하는 버전입니다.");
        }

        version.setVersion(request.version());
        version.setContent(request.content());

        TermVersion saved = termVersionRepository.save(version);
        log.info("약관 버전 수정: versionId={}, version={}", versionId, saved.getVersion());
        return TermVersionAdminResponse.from(saved);
    }

    @Transactional(transactionManager = "userTransactionManager")
    public void deleteTermVersion(Long versionId) {
        if (!termVersionRepository.existsById(versionId)) {
            throw new CustomException("404", "약관 버전을 찾을 수 없습니다.");
        }
        termVersionRepository.deleteById(versionId);
        log.info("약관 버전 삭제: versionId={}", versionId);
    }

    // ==================== User Term Agreements ====================

    public List<UserTermAgreementAdminResponse> getUserAgreements(String userId) {
        return userTermAgreementsRepository.findByUserIdWithTerms(userId).stream()
            .map(UserTermAgreementAdminResponse::from)
            .collect(Collectors.toList());
    }

    public List<UserTermAgreementAdminResponse> getUserAgreementsByTerms(String userId, Long termsId) {
        if (!termsRepository.existsById(termsId)) {
            throw new CustomException("404", "약관을 찾을 수 없습니다.");
        }
        return userTermAgreementsRepository.findByUserIdAndTermsId(userId, termsId).stream()
            .map(UserTermAgreementAdminResponse::from)
            .collect(Collectors.toList());
    }

    public Long getAgreementCountByTermVersion(Long termVersionId) {
        if (!termVersionRepository.existsById(termVersionId)) {
            throw new CustomException("404", "약관 버전을 찾을 수 없습니다.");
        }
        return userTermAgreementsRepository.countAgreedByTermVersionId(termVersionId);
    }

    public Long getAgreementCountByTerms(Long termsId) {
        if (!termsRepository.existsById(termsId)) {
            throw new CustomException("404", "약관을 찾을 수 없습니다.");
        }
        return userTermAgreementsRepository.countDistinctUsersByTermsIdAndAgreed(termsId);
    }

    public UserTermAgreementAdminPageResponse searchAllAgreements(
            String userId, Long termsId, Boolean isAgreed, Pageable pageable) {
        Page<UserTermAgreementAdminResponse> page = userTermAgreementsRepository.searchAgreementsWithFetch(
                userId, termsId, isAgreed, pageable)
            .map(UserTermAgreementAdminResponse::from);
        return UserTermAgreementAdminPageResponse.from(page);
    }

    public UserAgreementSummaryAdminPageResponse getUserAgreementSummaries(String keyword, Pageable pageable) {
        Page<String> userIds = userTermAgreementsRepository.findDistinctUserIds(keyword, pageable);

        Page<UserAgreementSummaryAdminResponse> summaryPage = userIds.map(userId ->
            new UserAgreementSummaryAdminResponse(
                userId,
                userTermAgreementsRepository.countByUsersId(userId),
                userTermAgreementsRepository.countAgreedByUsersId(userId),
                userTermAgreementsRepository.countRequiredTermsByUsersId(userId),
                userTermAgreementsRepository.countRequiredAgreedByUsersId(userId),
                userTermAgreementsRepository.findLastAgreedAtByUsersId(userId)
            ));

        return UserAgreementSummaryAdminPageResponse.from(summaryPage);
    }
}
