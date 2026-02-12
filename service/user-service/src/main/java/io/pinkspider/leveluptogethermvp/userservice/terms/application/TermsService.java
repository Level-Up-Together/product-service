package io.pinkspider.leveluptogethermvp.userservice.terms.application;

import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.RecentTermsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.TermAgreementsByUserResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.TermsRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TermsService {

    private final TermsRepository termsRepository;

    public List<RecentTermsResponseDto> getRecentAllTerms() {
        List<Object[]> rawResults = termsRepository.getRecentAllTermsRaw();
        return rawResults.stream()
            .map(this::mapToRecentTermsDto)
            .collect(Collectors.toList());
    }

    /**
     * Object[] 결과를 RecentTermsResponseDto로 매핑
     * 컬럼 순서: term_id(0), term_title(1), code(2), type(3), is_required(4), version_id(5), version(6), created_at(7), content(8)
     */
    private RecentTermsResponseDto mapToRecentTermsDto(Object[] row) {
        return new RecentTermsResponseDto(
            (Number) row[0],       // term_id
            (String) row[1],       // term_title
            (String) row[2],       // code
            (String) row[3],       // type
            (Boolean) row[4],      // is_required
            (Number) row[5],       // version_id
            (String) row[6],       // version
            row[7],                // created_at (Object - Timestamp 또는 null)
            (String) row[8]        // content
        );
    }

    public List<TermAgreementsByUserResponseDto> getTermAgreementsByUser(String userId) {
        List<Object[]> rawResults = termsRepository.getTermAgreementsByUserRaw(userId);
        return rawResults.stream()
            .map(this::mapToTermAgreementDto)
            .collect(Collectors.toList());
    }

    /**
     * 사용자가 아직 동의하지 않은 약관 목록 조회
     */
    public List<TermAgreementsByUserResponseDto> getPendingTermsByUser(String userId) {
        List<Object[]> rawResults = termsRepository.getPendingTermsByUserRaw(userId);
        return rawResults.stream()
            .map(this::mapToTermAgreementDto)
            .collect(Collectors.toList());
    }

    /**
     * Object[] 결과를 TermAgreementsByUserResponseDto로 매핑
     * 컬럼 순서: term_id(0), term_title(1), is_required(2), latest_version_id(3), version(4), is_agreed(5), agreed_at(6)
     */
    private TermAgreementsByUserResponseDto mapToTermAgreementDto(Object[] row) {
        return new TermAgreementsByUserResponseDto(
            (Number) row[0],       // term_id
            (String) row[1],       // term_title
            (Boolean) row[2],      // is_required
            (Number) row[3],       // latest_version_id
            (String) row[4],       // version
            (Boolean) row[5],      // is_agreed
            row[6]                 // agreed_at (Object - Timestamp 또는 null)
        );
    }
}
