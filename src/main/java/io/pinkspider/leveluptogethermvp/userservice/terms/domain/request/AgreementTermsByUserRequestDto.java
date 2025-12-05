package io.pinkspider.leveluptogethermvp.userservice.terms.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class AgreementTermsByUserRequestDto {

    private List<AgreementTerms> AgreementTermsList;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    public static class AgreementTerms {

        private Long termVersionId;

        @JsonProperty("is_agreed")
        private boolean isAgreed;
    }
}
