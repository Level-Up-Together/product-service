package io.pinkspider.leveluptogethermvp.userservice.terms.application;

import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.RecentTermsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.TermAgreementsByUserResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.TermsRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TermsService {

    private final TermsRepository termsRepository;

    public List<RecentTermsResponseDto> getRecentAllTerms() {
        return termsRepository.getRecentAllTerms();
    }

    public List<TermAgreementsByUserResponseDto> getTermAgreementsByUser(String userId) {
        return termsRepository.getTermAgreementsByUser(userId);
    }

    /**
     * 사용자가 아직 동의하지 않은 약관 목록 조회
     */
    public List<TermAgreementsByUserResponseDto> getPendingTermsByUser(String userId) {
        return termsRepository.getPendingTermsByUser(userId);
    }
}
