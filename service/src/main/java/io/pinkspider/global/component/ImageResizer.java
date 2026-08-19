package io.pinkspider.global.component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

/**
 * 업로드 이미지의 리사이즈 변형(thumb/medium)을 생성한다 (LUT-400).
 *
 * <p>원본이 목표 크기보다 이미 작거나 같으면 변형을 만들지 않는다(불필요한 업스케일 방지). 디코딩/리사이즈 실패(지원하지 않는 포맷 등)는 예외를 던지지 않고 빈 결과를
 * 반환한다 — 변형 생성은 부가 최적화이며 원본 업로드 자체를 막아서는 안 된다.
 */
@Slf4j
@Component
public class ImageResizer {

    public static final int THUMBNAIL_MAX_DIMENSION = 360;
    public static final int MEDIUM_MAX_DIMENSION = 1080;

    private static final double OUTPUT_QUALITY = 0.85;

    public Optional<byte[]> resize(byte[] originalBytes, String formatName, int maxDimension) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(originalBytes));
        } catch (IOException e) {
            log.warn("이미지 디코딩 실패, 변형 생성 스킵: {}", e.getMessage());
            return Optional.empty();
        }

        if (image == null) {
            log.warn("지원하지 않는 이미지 포맷, 변형 생성 스킵: format={}", formatName);
            return Optional.empty();
        }

        if (Math.max(image.getWidth(), image.getHeight()) <= maxDimension) {
            return Optional.empty();
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(image)
                    .size(maxDimension, maxDimension)
                    .outputFormat(formatName)
                    .outputQuality(OUTPUT_QUALITY)
                    .toOutputStream(outputStream);
            return Optional.of(outputStream.toByteArray());
        } catch (IOException e) {
            log.warn("이미지 리사이즈 실패, 변형 생성 스킵: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
