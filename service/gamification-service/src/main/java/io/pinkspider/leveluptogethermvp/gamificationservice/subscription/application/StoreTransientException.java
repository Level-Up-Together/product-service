package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

/**
 * LUT-617: 스토어 서버 API 의 <b>일시</b> 실패(5xx·타임아웃·연결 실패·토큰 발급 지연).
 *
 * <p>{@code CustomException(120702)} 은 "재시도해도 같은 결과"(잘못된 토큰·상품 불일치)에만 쓴다. 웹훅은 이 예외를 잡지 않고 5xx 로 응답해
 * Pub/Sub 가 백오프 재시도하게 한다 — 예전엔 모든 실패를 120702 로 감싸 RTDN 을 조용히 폐기했다. 클라이언트 verify 경로는 이 예외를 120702 로
 * 바꿔 응답 계약을 유지한다.
 */
public class StoreTransientException extends RuntimeException {

    public StoreTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
