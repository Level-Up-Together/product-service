package io.pinkspider.leveluptogethermvp.userservice.oauth.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenRefreshEvent {

    private String userId;
    private String deviceType;
    private String deviceId;
    private boolean refreshTokenRenewed;
    private long remainingTime;
}
