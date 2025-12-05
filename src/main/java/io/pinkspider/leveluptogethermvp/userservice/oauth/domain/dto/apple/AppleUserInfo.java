package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.apple;

import com.nimbusds.jwt.JWTClaimsSet;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.OAuth2UserInfo;
import java.text.ParseException;
import lombok.Getter;

@Getter
public class AppleUserInfo implements OAuth2UserInfo {

    private final String id;
    private final String name; // Apple은 name을 제공하지 않음
    private final String email;

    public AppleUserInfo(JWTClaimsSet claims) throws ParseException {
        this.id = claims.getSubject(); // "sub" 값 (Apple 사용자 ID)
        this.email = claims.getStringClaim("email");
        this.name = ""; // Apple은 기본적으로 name을 제공하지 않음
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getProvider() {
        return "apple";
    }
}


