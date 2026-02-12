package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ReissueJwtRequestDto {

    private String refreshToken;
}
