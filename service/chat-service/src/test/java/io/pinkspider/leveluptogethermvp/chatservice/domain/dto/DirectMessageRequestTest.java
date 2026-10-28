package io.pinkspider.leveluptogethermvp.chatservice.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LUT-590: DM 요청은 본문 또는 사진 중 하나만 있으면 유효하다 (예전엔 본문 필수라 사진만 보낸 DM 이 400) */
class DirectMessageRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("본문만 있으면 유효 — 구앱 요청 형식 그대로")
    void contentOnly() {
        assertThat(validator.validate(DirectMessageRequest.builder().content("안녕").build()))
                .isEmpty();
    }

    @Test
    @DisplayName("사진만 있어도 유효")
    void imageOnly() {
        assertThat(
                        validator.validate(
                                DirectMessageRequest.builder()
                                        .imageUrl("/uploads/chats/1/u/a.jpg")
                                        .build()))
                .isEmpty();
    }

    @Test
    @DisplayName("둘 다 비었거나 공백이면 거부")
    void neither() {
        assertThat(validator.validate(DirectMessageRequest.builder().build())).hasSize(1);
        assertThat(
                        validator.validate(
                                DirectMessageRequest.builder().content("  ").imageUrl(" ").build()))
                .hasSize(1);
    }
}
