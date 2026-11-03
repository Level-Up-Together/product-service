package io.pinkspider.global.image;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 파일이 실제 이미지인지 헤더 시그니처(매직 바이트)로 판정한다 (LUT-710).
 *
 * <p>왜 {@code ImageIO.read} 가 아니라 시그니처인가: ImageIO 는 WebP·HEIC 를 기본으로 못 읽어 정상 사진을 거절하고, 전체 디코딩은 검수
 * 모델이 어차피 다시 한다. 여기서는 앞 32바이트만 읽어 "이미지 컨테이너인가" 만 본다. 확장자·Content-Type 정책(허용 목록)은 각 저장소의 {@code
 * isValidImage} 가 그대로 맡는다 — 이 검사는 그 앞단에서 내용이 가짜인 파일을 걸러낼 뿐이다.
 *
 * <p>호출 위치: {@code ImageModerationAspect}(모든 {@code @ModerateImage} 업로드, 검수 on/off 와 무관) + 어드민 내부
 * 업로드 (상점 아이템·다이아 묶음 이미지). 빈 파일은 통과시킨다(각 저장소가 {@code error.image.empty} 로 처리).
 */
@Component
@Slf4j
public class ImageContentValidator {

    static final int HEADER_BYTES = 32;

    public enum ImageFormat {
        JPEG,
        PNG,
        GIF,
        WEBP,
        BMP,
        HEIF
    }

    /** 이미지가 아니면 {@link InvalidImageException}(400). */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        Optional<ImageFormat> format = detect(file);
        if (format.isEmpty()) {
            log.warn(
                    "이미지 아닌 파일 업로드 거절: file={}, contentType={}, size={}",
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize());
            throw InvalidImageException.notImage();
        }
    }

    public Optional<ImageFormat> detect(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return detect(in.readNBytes(HEADER_BYTES));
        } catch (IOException e) {
            log.warn("이미지 헤더 읽기 실패: file={}, error={}", file.getOriginalFilename(), e.getMessage());
            return Optional.empty();
        }
    }

    /** 헤더 바이트만으로 판정. 패키지 공개는 테스트용. */
    static Optional<ImageFormat> detect(byte[] h) {
        if (h == null || h.length < 4) {
            return Optional.empty();
        }
        if (startsWith(h, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(ImageFormat.JPEG);
        }
        if (startsWith(h, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(ImageFormat.PNG);
        }
        if (startsWith(h, 0x47, 0x49, 0x46, 0x38)) { // GIF8
            return Optional.of(ImageFormat.GIF);
        }
        if (h.length >= 12
                && startsWith(h, 0x52, 0x49, 0x46, 0x46) // RIFF
                && h[8] == 0x57
                && h[9] == 0x45
                && h[10] == 0x42
                && h[11] == 0x50) { // WEBP
            return Optional.of(ImageFormat.WEBP);
        }
        if (startsWith(h, 0x42, 0x4D)) { // BM
            return Optional.of(ImageFormat.BMP);
        }
        if (h.length >= 12
                && h[4] == 0x66
                && h[5] == 0x74
                && h[6] == 0x79
                && h[7] == 0x70) { // ....ftyp
            String brand =
                    new String(
                            Arrays.copyOfRange(h, 8, 12),
                            java.nio.charset.StandardCharsets.US_ASCII);
            if (brand.startsWith("hei")
                    || brand.startsWith("hev")
                    || brand.equals("mif1")
                    || brand.equals("msf1")
                    || brand.startsWith("avif")) {
                return Optional.of(ImageFormat.HEIF);
            }
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] h, int... prefix) {
        if (h.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((h[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
