package io.pinkspider.leveluptogethermvp.userservice.core.api;

import lombok.Getter;

/*
- code 할당 규칙
앞 2자리 - 서비스 일련번호
가운데 2자리 exception 카테고리
마지막 2자리 exception 일련번호

* global                 00 00 00
* level-up-together bff  01 00 00
* api-gateway            02 00 00
* user-service           03 00 00
* guild-service          04 00 00
* mission-service        05 00 00
* app-push-service       06 00 00
* payment-service        07 00 00
* meta-service           08 00 00
* logger-service         09 00 00
* stats-service          10 00 00
* batch-service          11 00 00
 */
public enum UserApiStatus {

    NOT_EXIST_USER("030101", "Not Exist User", "사용자가 없습니다."),

    NOT_VALID_ACCESS_TOKEN("010101", "Not Valid Access Token", "Access Token이 맞지 않습니다."),
    NOT_VALID_REFRESH_TOKEN("010102", "Not Valid Refresh Token", "Refresh Token이 맞지 않습니다."),
    NOT_EXIST_TOKEN("010103", "Not Exist Access Token In Header or Not Exist Refresh Token In Cookie", "헤더에 Access Token이 없습니다."),
    NO_REFRESH_TOKEN_OF_USER_UUID("030102", "No Refresh Token Of User Uuid", "사용자 고유번호에 등록된 리프레쉬 토큰이 없습니다."),
    NOT_MATCH_REFRESH_TOKEN_IN_REDIS("030103", "Not Match Refresh Token In Redis", "사용자 고유번호에 등록된 리프레쉬 토큰이 없습니다."),
    LOGOUT_FAILED("010203", "Logout Failed", "로그아웃 실패"),
    LOGOUT_ALL_FAILED("010204", "Logout All Failed", "전체 로그아웃 실패"),
    FAILED_TO_GET_SESSIONS("010205", "Failed To Get Sessions", "Failed To Get Sessions"),
    FAILED_TO_GET_TOKEN_STATUS("010206", "Failed To Get Token Status", "Failed To Get Token Status"),
    TOKEN_REISSUE_FAILED("010108", "Token Refresh Failed", "토큰 재발급 실패"),
    BLACKLISTED_JWT("010105", "Blacklisted JWT", "차단된 token 입니다."),
    TOKEN_EXCEEDED_MAXIMUM_LIFETIME("010106", "Token Exceeded Maximum Lifetime", "만료된 토큰 입니다."),
    TOKEN_CANNOT_BE_RENEWED("010107", "Token Cannot Be Renewed", "재발급 받을 수 없습니다."),

    ;
//    NOT_EXIST_INVESTOR("170001", "Not Exist Investor", "투자자가 없습니다."),
//    NOT_EXIST_BORROWER("170002", "Not Exist Borrower", "대출자가 없습니다."),
//    NOT_EXIST_USER_CORP("170003", "Not Exist User Corp", "사용자로 등록된 사업자가 없습니다."),
//    WRONG_USER_LOGIN_PASSWORD("170101", "Wrong Password", "비밀번호가 틀립니다."),
//    OVER_PASSWORD_FAIL("170102", "Over Password Fail", "OVER_PASSWORD_FAIL"),
//    DORMANCY_USER("170103", "Dormancy User", "휴면회원입니다."),
//    WITHDRAWAL_APPLY_USER("170104", "Withdrawal Apply User", "탈퇴신청중 회원입니다.;"),
//    WITHDRAWAL_USER("170105", "Withdrawal User", "탈퇴 회원입니다."),
//    BLACKLIST_USER("170106", "Blacklist User", "Blacklist 회원입니다."),
//    NOT_EXIST_USER_HOME_ADDRESS("170201", "Not Exist User Home Address", "사용자가 주소가 없습니다."),
//    NICE_ERROR("170010", "NICE ERROR", "NICE ERROR"),
//    NICE_CODE_NOT_FOUND("170011", "NICE CODE NOT FOUND", "NICE CODE NOT FOUND"),
//    USER_ALREADY_EXIST("170020", "USER ALREADY EXIST", "USER ALREADY EXIST"),
//    NOT_EXIST_INVESTOR_LIST("170100", "Not Exist Investor List", " 투자자 목록이 없습니다."),
//    NOT_EXIST_INVESTOR_CREDENTIAL_COUNTS("170101", "Not Exist Investor Credential Counts", "투자자 타입별 합계가 없습니다."),
//    NOT_EXIST_INVESTOR_DETAIL("170102", "Not Exist Investor Detail", "투자자 상세정보가 없습니다."),
//    NOT_EXIST_INVESTOR_GRADE_LIST("170103", "Not Exist Investor Grade List", "투자자 등급변경신청 목록이 없습니다."),
//    NOT_EXIST_INVESTOR_GRADE_VERIFICATION_COUNTS("170104", "Not Exist Grade Verification Counts", "투자자 등급변경신청 진행상태 합계가 없습니다."),
//    NOT_EXIST_INVESTOR_GRADE_DETAIL("170105", "Not Exist Investor Grade Detail", "투자자 등급변경신청 상세가 없습니다."),
//    NOT_EXIST_USER_VERIFICATION("170106", "Not Exist User Verification", "회원 등급변경신청서가 없습니다."),
//    NOT_EXIST_CORPORATION_CONSULTATION_LIST("170107", "Not Exist Corporation Consultation List", "법인상담 목록이 없습니다."),
//    NOT_EXIST_CORPORATION_CONSULTATION_STATUS_COUNTS("170108", "Not Exist Corporation Consultation Status Counts", "법인상담 진행상태 합계가 없습니다."),
//    NOT_EXIST_CORPORATION_CONSULTATION_DETAIL("170109", "Not Exist Corporation Consultation Detail", "법인상담 상세가 없습니다."),
//    NOT_EXIST_BORROWER_LIST("170110", "Not Exist Borrower List", "대출자 목록이 없습니다."),
//    NOT_EXIST_BORROWER_CREDENTIAL_COUNTS("170111", "Not Exist Borrower Credential Counts", "대출자 타입별 합계가 없습니다."),
//    CANNOT_SET_USER_AUTHENTICATION("2001", "Cannot set user authentication", "사용자가 없습니다."),
//    NOT_EXIST_CACHED_USER_INFO("2002", "Not Exist Cached User Info", "사용자가 없습니다."),
//    NOT_VALID_ACCESS_TOKEN("2100", "Not Valid Access Token", "Access Token이 맞지 않습니다."),
//    MALFORMED_JWT("2101", "Malformed Jwt Exception", "JWT가 변조되었습니다."),
//    EXPIRED_JWT_TOKEN("2102", "Expired Jwt Token", "JWT가 만료되었습니다."),
//    UNSUPPORTED_JWT_TOKEN("2103", "Unsupported Jwt Token", "지원되지 않는 JWT 입니다."),
//    JWT_CLAIMS_STRING_EMPTY("2104", "Jwt Claims String Empty", "JWT Claim에 값이 없습니다."),
//    NOT_EXIST_TOKEN("2105", "Not Exist Access Token In Header or Not Exist Refresh Token In Cookie", "헤더에 Access Token이 없습니다."),
//    NOT_VALID_REFRESH_TOKEN("2200", "Not Valid Refresh Token", "Refresh Token이 맞지 않습니다."),
//    NO_REFRESH_TOKEN_OF_USER_UUID("2201", "No Refresh Token Of User Uuid", "사용자 고유번호에 등록된 리프레쉬 토큰이 없습니다."),
//    NOT_EXIST_REFRESH_TOKEN("2203", "Refresh Token Is Not Exist", "사용자 고유번호에 등록된 리프레쉬 토큰이 없습니다."),
//    INVALID_TOKEN("2300", "Invalid Token", "부적합한 토큰입니다."),
//    NOT_EXIST_BORROWER_DETAIL("170112", "Not Exist Borrower Detail", "대출자 상세가 없습니다.");

    @Getter
    private final String resultCode;

    @Getter
    private final String resultMessage;

    @Getter
    private final String description;

    UserApiStatus(String resultCode, String resultMessage, String description) {
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.description = description;
    }
}
