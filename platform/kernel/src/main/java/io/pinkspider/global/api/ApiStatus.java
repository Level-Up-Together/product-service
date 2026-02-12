package io.pinkspider.global.api;

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

public enum ApiStatus {
    OK("000000", "Success", "정상처리"),
    SYSTEM_ERROR("000500", "System Error", "시스템 내 오류"),
    CLIENT_ERROR("000600", "Client Error", "사용자 오류"),
    MESSAGING_SEND_FAIL("000601", "Client Error", "사용자 오류"),
    EXTERNAL_API_ERROR("000610", "External Api Error", "외부 API 오류"),
    FEIGN_CLIENT_CALL_ERROR("000611", "External Api Error", "외부 API 오류"),
    FEIGN_EXCEPTION("000612", "Feign Exception", "외부 API 오류"),
    INVALID_INPUT("000710", "Invalid Input Error", "유효하지 않은 입력"),
    INVALID_ACCESS("000720", "Invalid Access Error", "유효하지 않은 접근"),
    EXCEEDED_FILE_SIZE("000800", "File Size Exceeded", "제한 파일 사이즈 초과"),
    FILE_ALREADY_EXIST("000801", "File Already Exist", "해당 경로에 같은 파일명이 이미 존재"),
    FILE_UPLOAD_PROCESS_ERROR("000802", "File Upload Process Error", "파일 업로드중 오류가 발생"),
    FILE_NOT_EXIST("000803", "File Not Exist", "파일이 존재하지 않음"),
    FILE_UNKNOWN_ERROR("000804", "File Unknown Error", "파일 알수 없는 오류"),
    FILE_IO_EXCEPTION("000805", "File IO EXCEPTION", "파일 읽기 오류"),
    CRYPTO_ENCRYPT_ERROR("000810", "Crypto Encrypt Error", "암호화 오류"),
    CRYPTO_DECRYPT_ERROR("000811", "Crypto Decrypt Error", "암호화 오류"),
    JSON_PARSE_ERROR("000820", "JSON PARSE ERROR", "JSON PARSE ERROR"),
    NOT_EXIST_PLATFORM_CRYPTO_META_DATA("004100", "not exist platform crypto meta data", "부적합한 토큰입니다."),
    NOT_EXIST_COMMON_CODE("004101", "Not Exist Common Code", "해당 공통코드가 존재하지 않습니다."),
    MALFORMED_JWT("002101", "Malformed Jwt Exception", "JWT가 변조되었습니다."),
    EXPIRED_JWT_TOKEN("002102", "Expired Jwt Token", "JWT가 만료되었습니다."),
    UNSUPPORTED_JWT_TOKEN("002103", "Unsupported Jwt Token", "지원되지 않는 JWT 입니다."),
    JWT_CLAIMS_STRING_EMPTY("002104", "Jwt Claims String Empty", "JWT Claim에 값이 없습니다.");

    @Getter
    private final String resultCode;

    @Getter
    private final String resultMessage;

    @Getter
    private final String description;

    ApiStatus(String resultCode, String resultMessage, String description) {
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.description = description;
    }
}
