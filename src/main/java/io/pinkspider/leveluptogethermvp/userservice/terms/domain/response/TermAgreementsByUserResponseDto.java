package io.pinkspider.leveluptogethermvp.userservice.terms.domain.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 약관 동의 상태 응답 DTO
 * - Native Query 결과 매핑용 class-based DTO
 * - Interface projection 대신 class 사용 (TupleBackedMap 버그 방지)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TermAgreementsByUserResponseDto {

    private String termId;
    private String termTitle;
    private Boolean isRequired;
    private String latestVersionId;
    private String version;
    private Boolean isAgreed;
    private String agreedAt;

    // Native query 결과 매핑을 위한 생성자 (컬럼 순서대로)
    public TermAgreementsByUserResponseDto(
            Number termId,
            String termTitle,
            Boolean isRequired,
            Number latestVersionId,
            String version,
            Boolean isAgreed,
            Object agreedAt) {
        this.termId = termId != null ? termId.toString() : null;
        this.termTitle = termTitle;
        this.isRequired = isRequired != null ? isRequired : false;
        this.latestVersionId = latestVersionId != null ? latestVersionId.toString() : null;
        this.version = version;
        this.isAgreed = isAgreed != null ? isAgreed : false;
        this.agreedAt = agreedAt != null ? agreedAt.toString() : null;
    }
}
