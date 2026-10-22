package io.pinkspider.leveluptogethermvp.chatservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** LUT-590: 채팅 이미지 파일 검증 — 로컬·S3 저장소가 공유한다 */
class ChatImageFileValidatorTest {

    private ChatImageProperties properties;
    private ChatImageFileValidator validator;

    @BeforeEach
    void setUp() {
        properties = new ChatImageProperties();
        properties.setMaxSize(100);
        validator = new ChatImageFileValidator(properties);
    }

    private MockMultipartFile file(String name, String contentType, int size) {
        return new MockMultipartFile("image", name, contentType, new byte[size]);
    }

    @Test
    @DisplayName("통과하면 소문자 확장자를 돌려준다")
    void validate_success() {
        assertThat(validator.validate(file("Photo.JPG", "image/jpeg", 10))).isEqualTo("jpg");
    }

    @Test
    @DisplayName("파일이 null 이거나 비었으면 error.image.empty")
    void validate_empty() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.image.empty");
        assertThatThrownBy(() -> validator.validate(file("a.jpg", "image/jpeg", 0)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.image.empty");
    }

    @Test
    @DisplayName("최대 크기를 넘으면 error.image.invalid")
    void validate_tooLarge() {
        assertThatThrownBy(() -> validator.validate(file("a.jpg", "image/jpeg", 101)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.image.invalid");
    }

    @Test
    @DisplayName("허용되지 않은 확장자·확장자 없음은 거부")
    void validate_badExtension() {
        assertThatThrownBy(() -> validator.validate(file("a.exe", "image/jpeg", 10)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validator.validate(file("noext", "image/jpeg", 10)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validator.validate(file(null, "image/jpeg", 10)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("이미지가 아닌 MIME 이나 MIME 없음은 거부")
    void validate_badMime() {
        assertThatThrownBy(() -> validator.validate(file("a.jpg", "text/plain", 10)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validator.validate(file("a.jpg", null, 10)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("insertSuffix 는 확장자 앞에, 확장자가 없으면 끝에 붙인다")
    void insertSuffix() {
        assertThat(ChatImageFileValidator.insertSuffix("a/b.jpg", "_thumb"))
                .isEqualTo("a/b_thumb.jpg");
        assertThat(ChatImageFileValidator.insertSuffix("a/b", "_thumb")).isEqualTo("a/b_thumb");
    }
}
