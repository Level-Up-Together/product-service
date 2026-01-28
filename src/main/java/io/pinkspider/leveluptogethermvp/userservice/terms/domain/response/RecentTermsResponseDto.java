package io.pinkspider.leveluptogethermvp.userservice.terms.domain.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 최근 약관 목록 응답 DTO
 * - Native Query 결과 매핑용 class-based DTO
 * - Interface projection 대신 class 사용 (TupleBackedMap 버그 방지)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecentTermsResponseDto {

    private String termId;
    private String termTitle;
    private String code;
    private String type;
    private Boolean isRequired;
    private String versionId;
    private String version;
    private String createdAt;
    private String content;

    // Native query 결과 매핑을 위한 생성자 (컬럼 순서대로)
    public RecentTermsResponseDto(
            Number termId,
            String termTitle,
            String code,
            String type,
            Boolean isRequired,
            Number versionId,
            String version,
            Object createdAt,
            String content) {
        this.termId = termId != null ? termId.toString() : null;
        this.termTitle = termTitle;
        this.code = code;
        this.type = type;
        this.isRequired = isRequired != null ? isRequired : false;
        this.versionId = versionId != null ? versionId.toString() : null;
        this.version = version;
        this.createdAt = createdAt != null ? createdAt.toString() : null;
        this.content = content;
    }
}
