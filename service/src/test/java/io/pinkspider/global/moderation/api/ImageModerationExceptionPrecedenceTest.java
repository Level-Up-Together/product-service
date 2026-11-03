package io.pinkspider.global.moderation.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.handler.RestExceptionHandler;
import io.pinkspider.global.moderation.exception.ImageModerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영과 같은 예외 처리 순서(플랫폼 {@link RestExceptionHandler} + 검수 어드바이스)에서 검수 거절이 500 이 아니라 400/503 으로 나가는지.
 * {@code @WebMvcTest} 슬라이스에는 플랫폼 핸들러가 없어 단위 테스트만으로는 500 이 보이지 않는다(LUT-616 교훈) — 그래서 둘을 함께 올린다. 대조군:
 * 일반 {@link CustomException} 은 여전히 플랫폼 핸들러가 500 으로 처리한다(이 어드바이스가 다른 예외를 가로채지 않는다).
 */
@DisplayName("ImageModerationExceptionHandler 우선순위 (LUT-714, 플랫폼 핸들러와 함께)")
class ImageModerationExceptionPrecedenceTest {

    @RestController
    static class UploadStubController {
        @PostMapping("/stub/blocked")
        String blocked() {
            throw ImageModerationException.blocked();
        }

        @PostMapping("/stub/unavailable")
        String unavailable() {
            throw ImageModerationException.unavailable();
        }

        @PostMapping("/stub/other")
        String other() {
            throw new CustomException("999999", "error.other");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new UploadStubController())
                        .setControllerAdvice(
                                new RestExceptionHandler(),
                                new ImageModerationExceptionHandler(null))
                        .build();
    }

    @Test
    @DisplayName("부적절한 이미지 → 400 000010 (플랫폼 핸들러의 500 을 앞질러 처리)")
    void blockedIs400() throws Exception {
        mockMvc.perform(post("/stub/blocked"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("000010"))
                .andExpect(jsonPath("$.message").value("error.moderation.inappropriate_image"));
    }

    @Test
    @DisplayName("검수 불가 → 503 000011")
    void unavailableIs503() throws Exception {
        mockMvc.perform(post("/stub/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("000011"));
    }

    @Test
    @DisplayName("다른 CustomException 은 그대로 플랫폼 핸들러가 500 으로 처리한다")
    void otherCustomExceptionStays500() throws Exception {
        mockMvc.perform(post("/stub/other"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("999999"));
    }
}
