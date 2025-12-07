package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuildJoinRequestDto {

    @Size(max = 500, message = "가입 신청 메시지는 500자 이하여야 합니다.")
    private String message;
}
