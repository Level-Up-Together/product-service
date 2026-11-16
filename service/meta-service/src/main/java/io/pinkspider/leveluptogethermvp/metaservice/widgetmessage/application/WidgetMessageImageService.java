package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application;

import io.pinkspider.global.image.ImageContentValidator;
import io.pinkspider.global.image.ImageContentValidator.ImageFormat;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.image.WidgetImageStorageService;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageImageUploadResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * LUT-797: 어드민 위젯 문장 배경 이미지 업로드. 내용이 진짜 JPG·PNG 인지 헤더로 확인(확장자·Content-Type 은 믿지 않는다) → 저장 → 크기 읽기.
 * 위젯(iOS WidgetKit·안드로이드 Glance)이 바로 그릴 수 있는 형식만 받는다. 어드민 내부 업로드라 {@code @ModerateImage} 검수는 없다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WidgetMessageImageService {
    private final WidgetImageStorageService storage;
    private final ImageContentValidator imageContentValidator;

    public WidgetMessageImageUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw WidgetMessageImageException.invalid();
        }
        Optional<ImageFormat> format = imageContentValidator.detect(file);
        if (format.isEmpty()
                || (format.get() != ImageFormat.JPEG && format.get() != ImageFormat.PNG)) {
            log.warn(
                    "위젯 배경 이미지 형식 거절: file={}, format={}",
                    file.getOriginalFilename(),
                    format.orElse(null));
            throw WidgetMessageImageException.invalid();
        }
        int[] size = readSize(file);
        String url = storage.store(file);
        log.info(
                "위젯 배경 이미지 업로드: url={}, size={}x{}, bytes={}",
                url,
                size == null ? null : size[0],
                size == null ? null : size[1],
                file.getSize());
        return new WidgetMessageImageUploadResponse(
                url, size == null ? null : size[0], size == null ? null : size[1], file.getSize());
    }

    /** 픽셀을 디코딩하지 않고 헤더만 읽는다. 읽기 실패는 null(업로드는 막지 않는다). */
    static int[] readSize(MultipartFile file) {
        try (InputStream in = file.getInputStream();
                ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            log.warn(
                    "위젯 배경 이미지 크기 읽기 실패: file={}, error={}",
                    file.getOriginalFilename(),
                    e.getMessage());
            return null;
        }
    }
}
