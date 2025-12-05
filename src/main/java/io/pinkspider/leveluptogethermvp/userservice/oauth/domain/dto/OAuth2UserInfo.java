package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto;

public interface OAuth2UserInfo {

    String getId();       // 공급자의 사용자 ID

    String getName();     // 사용자 이름

    String getEmail();    // 사용자 이메일

    String getProvider(); // 공급자 이름 (google, kakao, apple)
}

