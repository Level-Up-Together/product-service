package io.pinkspider.global.feign.admin;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * admin-service 의 product 전용 경로(유저 문의 {@code /api/v1/inquiries/**}·내부 API {@code
 * /api/internal/**}·신고 등록/검토 확인)가 요구하는 공유 시크릿 헤더 {@code X-Internal-Api-Key} 를 싣는다 (LUT-686).
 *
 * <p>값은 product 가 자기 내부 API 검증에 쓰는 {@code app.security.internal-api.key} 와 같다 — admin 이 product 를
 * 부를 때 쓰는 {@code app.mvp.internal-api-key} 와 같은 값이라 admin 쪽은 새 설정 없이 그 키로 검증한다. 예전엔 이 경로들이 IP 허용
 * 목록(비어 있으면 전체 허용)에만 의존해 누구나 {@code X-User-Id} 만 바꿔 남의 문의를 읽고 쓸 수 있었다. 키가 비어 있으면(local) 헤더를 싣지 않는다
 * — admin 도 키가 비어 있으면 검사를 건너뛴다(fail-open).
 *
 * <p>admin 을 부르는 Feign 설정 3개(AdminFeignConfig·ReportFeignConfig·AdminInternalFeignConfig)가
 * {@code @Bean} 으로 등록한다. 설정 클래스에 {@code @Configuration} 을 붙이지 말 것(LUT-571) — 이 인터셉터가 카카오·구글 등 외부
 * 호출에도 전역 적용돼 내부 키가 밖으로 나간다.
 */
public final class AdminInternalApiKeyInterceptor implements RequestInterceptor {

    public static final String HEADER_NAME = "X-Internal-Api-Key";

    private final String internalApiKey;

    public AdminInternalApiKeyInterceptor(String internalApiKey) {
        this.internalApiKey = internalApiKey == null ? "" : internalApiKey;
    }

    public boolean isConfigured() {
        return !internalApiKey.isBlank();
    }

    @Override
    public void apply(RequestTemplate template) {
        if (isConfigured()) {
            template.header(HEADER_NAME, internalApiKey);
        }
    }
}
