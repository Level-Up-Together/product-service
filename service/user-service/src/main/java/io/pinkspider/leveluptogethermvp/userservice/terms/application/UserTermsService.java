package io.pinkspider.leveluptogethermvp.userservice.terms.application;


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
                    userTermAgreement.setIsAgreed(agreementTerms.isAgreed());
                    userTermAgreementsService.save(userTermAgreement);
                } else {
                    Users user = userService.findByUserId(userId);
                    TermVersion termVersion = termVersionService.findById(agreementTerms.getTermVersionId());

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
}
