package io.pinkspider.leveluptogethermvp.bffservice.api.exception;

import lombok.Getter;

/**
 * LUT-804: 시즌 상세 조회에서 시즌이 없다 — 진행 중인 시즌이 없거나({@code NO_ACTIVE_SEASON}) 그 ID 의 시즌이 없다({@code
 * SEASON_NOT_FOUND}).
 *
 * <p>플랫폼 {@code CustomException} 은 항상 500 + error 로그다. 시즌 조회를 비로그인에게 열면서 웹 서버가 방문자의 {@code /season}
 * 요청·사이트맵 요청마다 이 API 를 부르게 됐는데, "지금 시즌이 없다"는 정상 상황이라 500 으로 두면 오류 지표와 로그가 그 호출 수만큼 쌓인다. 그래서 {@link
 * BffSeasonExceptionHandler} 가 404 로 응답한다. 본문의 {@code code}·{@code message} 는 예전과 같다(클라이언트는 코드로
 * 구분한다).
 */
@Getter
public class SeasonNotFoundException extends RuntimeException {

    /** 진행 중인 시즌이 없음 — 웹이 이 코드로 "시즌 없음" 화면을 고른다 */
    public static final String CODE_NO_ACTIVE = "NO_ACTIVE_SEASON";

    /** 그 ID 의 시즌이 없음 */
    public static final String CODE_NOT_FOUND = "SEASON_NOT_FOUND";

    private final String code;

    private SeasonNotFoundException(String code, String messageKey) {
        super(messageKey);
        this.code = code;
    }

    public static SeasonNotFoundException noActive() {
        return new SeasonNotFoundException(CODE_NO_ACTIVE, "error.season.no_active");
    }

    public static SeasonNotFoundException notFound() {
        return new SeasonNotFoundException(CODE_NOT_FOUND, "error.season.not_found");
    }
}
