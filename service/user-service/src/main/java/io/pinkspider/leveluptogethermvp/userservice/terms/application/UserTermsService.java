package io.pinkspider.leveluptogethermvp.userservice.terms.application;


import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.request.AgreementTermsByUserRequestDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.RecentTermsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.TermAgreementsByUserResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.UserService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.TermVersion;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserTermAgreement;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserTermsService {

    private final UserService userService;
    private final TermsService termsService;
    private final TermVersionService termVersionService;
    private final UserTermAgreementsService userTermAgreementsService;

    public List<RecentTermsResponseDto> getRecentAllTerms() {
        return termsService.getRecentAllTerms();
    }

    public List<TermAgreementsByUserResponseDto> getTermAgreementsByUser(String userId) {
        return termsService.getTermAgreementsByUser(userId);
    }

    /**
     * 사용자가 아직 동의하지 않은 약관 목록 조회
     * (약관 버전 업데이트 시 새로 동의가 필요한 약관만 반환)
     */
    public List<TermAgreementsByUserResponseDto> getPendingTermsByUser(String userId) {
        return termsService.getPendingTermsByUser(userId);
    }

    @Transactional
    public void agreementTermsByUser(String userId, AgreementTermsByUserRequestDto requestDto) {
        requestDto.getAgreementTermsList().forEach(
            agreementTerms -> {
                UserTermAgreement userTermAgreement = userTermAgreementsService.findAllByUserIdAndTermVersionId(userId,
                    agreementTerms.getTermVersionId());

                if (userTermAgreement != null) {
                    validatePublished(userTermAgreement.getTermVersion());
                    userTermAgreement.setIsAgreed(agreementTerms.isAgreed());
                    userTermAgreementsService.save(userTermAgreement);
                } else {
                    Users user = userService.findByUserId(userId);
                    TermVersion termVersion = termVersionService.findById(agreementTerms.getTermVersionId());
                    validatePublished(termVersion);

                    UserTermAgreement entity = UserTermAgreement.builder()
                        .users(user)
                        .termVersion(termVersion)
                        .isAgreed(agreementTerms.isAgreed()) // null 방지
                        .build();

                    userTermAgreementsService.save(entity);
                }
            }
        );
    }

    // 게시 전(DRAFT) 버전은 유저에게 노출된 적이 없으므로 동의 대상이 될 수 없다 (LUT-364)
    private void validatePublished(TermVersion termVersion) {
        if (!termVersion.isPublished()) {
            throw new CustomException("400", "error.terms.version.not_published");
        }
    }

    /**
     * LUT-366: 필수 약관 전부에 최신 게시 버전 기준으로 동의했는지 검증 (가입 성립 조건).
     * 만 15세 확인처럼 법적 효력이 필요한 필수 동의가 화면 게이트 우회로 빠지지 않게 서버에서 강제한다.
     *
     * @param agreedVersionIds is_agreed=true 로 동의한 약관 버전 ID 집합
     */
    public void validateRequiredTermsAgreed(java.util.Set<Long> agreedVersionIds) {
        List<String> missingTitles = getRecentAllTerms().stream()
            .filter(t -> Boolean.TRUE.equals(t.getIsRequired()))
            .filter(t -> !agreedVersionIds.contains(Long.valueOf(t.getVersionId())))
            .map(RecentTermsResponseDto::getTermTitle)
            .toList();

        if (!missingTitles.isEmpty()) {
            log.warn("필수 약관 미동의 가입 시도 차단: missing={}", missingTitles);
            throw new CustomException("TERMS_001", "error.terms.required_not_agreed");
        }
    }
}
