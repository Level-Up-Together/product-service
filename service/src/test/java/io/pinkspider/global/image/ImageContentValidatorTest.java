package io.pinkspider.global.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.image.ImageContentValidator.ImageFormat;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("ImageContentValidator (LUT-710 업로드 파일 이미지 시그니처 검증)")
class ImageContentValidatorTest {

    private final ImageContentValidator validator = new ImageContentValidator();

    static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    static byte[] padded(byte[] head, int total) {
        byte[] out = new byte[total];
        System.arraycopy(head, 0, out, 0, head.length);
        return out;
    }

    @Nested
    @DisplayName("시그니처 판정")
    class Detect {
        @Test
        @DisplayName("JPEG·PNG·GIF·BMP 헤더를 알아본다")
        void commonFormats() {
            assertThat(ImageContentValidator.detect(padded(bytes(0xFF, 0xD8, 0xFF, 0xE0), 16)))
                    .contains(ImageFormat.JPEG);
            assertThat(
                            ImageContentValidator.detect(
                                    padded(
                                            bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
                                            16)))
                    .contains(ImageFormat.PNG);
            assertThat(ImageContentValidator.detect("GIF89a".getBytes(StandardCharsets.US_ASCII)))
                    .contains(ImageFormat.GIF);
            assertThat(ImageContentValidator.detect(padded(bytes(0x42, 0x4D), 16)))
                    .contains(ImageFormat.BMP);
        }

        @Test
        @DisplayName("WebP(RIFF....WEBP)·HEIC(....ftypheic)·AVIF 를 알아본다")
        void containerFormats() {
            byte[] webp = padded("RIFF".getBytes(StandardCharsets.US_ASCII), 16);
            System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);
            assertThat(ImageContentValidator.detect(webp)).contains(ImageFormat.WEBP);

            byte[] heic = padded(bytes(0x00, 0x00, 0x00, 0x18), 24);
            System.arraycopy("ftypheic".getBytes(StandardCharsets.US_ASCII), 0, heic, 4, 8);
            assertThat(ImageContentValidator.detect(heic)).contains(ImageFormat.HEIF);

            byte[] avif = padded(bytes(0x00, 0x00, 0x00, 0x1C), 24);
            System.arraycopy("ftypavif".getBytes(StandardCharsets.US_ASCII), 0, avif, 4, 8);
            assertThat(ImageContentValidator.detect(avif)).contains(ImageFormat.HEIF);
        }

        @Test
        @DisplayName("랜덤 바이트·텍스트·너무 짧은 파일·null 은 이미지가 아니다")
        void notImages() {
            assertThat(
                            ImageContentValidator.detect(
                                    "hello world, not an image".getBytes(StandardCharsets.UTF_8)))
                    .isEmpty();
            assertThat(ImageContentValidator.detect(bytes(0x13, 0x37, 0x42, 0x99, 0x00, 0x11)))
                    .isEmpty();
            assertThat(ImageContentValidator.detect(bytes(0xFF, 0xD8))).isEmpty();
            assertThat(ImageContentValidator.detect((byte[]) null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("validate")
    class Validate {
        @Test
        @DisplayName("확장자·Content-Type 이 이미지여도 내용이 아니면 000012 로 거절한다")
        void rejectsFakePng() {
            MockMultipartFile fake =
                    new MockMultipartFile(
                            "image",
                            "qa_bad.png",
                            "image/png",
                            "definitely not a png".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> validator.validate(fake))
                    .isInstanceOf(InvalidImageException.class)
                    .isInstanceOf(CustomException.class)
                    .satisfies(
                            e -> {
                                CustomException ce = (CustomException) e;
                                assertThat(ce.getCode()).isEqualTo("000012");
                                assertThat(ce.getMessage()).isEqualTo("error.image.not_image");
                            });
        }

        @Test
        @DisplayName("진짜 PNG 헤더면 통과한다 (확장자가 jpg 라도 내용만 본다)")
        void acceptsRealPng() {
            MockMultipartFile png =
                    new MockMultipartFile(
                            "image",
                            "photo.jpg",
                            "image/jpeg",
                            padded(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), 64));

            assertThatCode(() -> validator.validate(png)).doesNotThrowAnyException();
            assertThat(validator.detect(png)).isEqualTo(Optional.of(ImageFormat.PNG));
        }

        @Test
        @DisplayName("빈 파일·null 은 여기서 거절하지 않는다 (저장소가 error.image.empty 로 처리)")
        void emptyPassesThrough() {
            MockMultipartFile empty =
                    new MockMultipartFile("image", "e.png", "image/png", new byte[0]);

            assertThatCode(() -> validator.validate(empty)).doesNotThrowAnyException();
            assertThatCode(() -> validator.validate(null)).doesNotThrowAnyException();
        }
    }
}
