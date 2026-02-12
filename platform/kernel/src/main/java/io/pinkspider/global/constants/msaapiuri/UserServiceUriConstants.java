package io.pinkspider.global.constants.msaapiuri;

public class UserServiceUriConstants {

    //  ## naming rule
    //  package + uri
    //  path variable 의 경우 BY_~~~~

    public static final String USER_OAUTH_URI_BY_PROVIDER = "/uri/{provider}";
    public static final String USER_OAUTH_CALLBACK_BY_PROVIDER = "/callback/{provider}"; // createJwt
    public static final String USER_OAUTH_JWT_REISSUE = "/oauth/jwt/reissue";
    public static final String USER_OAUTH_LOGOUT = "/oauth/logout";
}
