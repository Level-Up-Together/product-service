package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferMasterRequest {

    @NotBlank(message = "새 길드 마스터 ID는 필수입니다.")
    private String newMasterId;
}
