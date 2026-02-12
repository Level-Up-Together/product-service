package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto;

public interface OAuth2UserInfo {

    String getId();       // 공급자의 사용자 ID

    String getNickname(); // 사용자 닉네임 (소셜 로그인에서 받은 이름)

    String getEmail();    // 사용자 이메일

    String getProvider(); // 공급자 이름 (google, kakao, apple)
}

