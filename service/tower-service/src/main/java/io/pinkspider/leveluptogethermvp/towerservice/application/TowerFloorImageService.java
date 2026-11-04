package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.image.ImageContentValidator;
import io.pinkspider.global.image.ImageContentValidator.ImageFormat;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.image.TowerImageStorageService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorImageUploadResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;
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
 * LUT-635: 어드민 층 이미지 업로드. 형식 검증(LUT-710) → 마스크는 PNG 만(픽셀을 그대로 읽어야 하므로 손실 압축 금지) → 저장 → 크기 읽기(어드민이
 * 짧은 변 1536px 미만 경고에 쓴다). 어드민 내부 업로드라 {@code @ModerateImage} 검수는 없다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TowerFloorImageService {
    private final TowerImageStorageService storage;
    private final ImageContentValidator imageContentValidator;

    public TowerFloorImageUploadResponse upload(MultipartFile file, TowerImageKind kind) {
        if (file == null || file.isEmpty() || kind == TowerImageKind.BGM) {
            throw TowerFloorException.imageInvalid(); // 배경 음악은 TowerFloorAudioService
        }
        imageContentValidator.validate(file);
        if (kind == TowerImageKind.MASK) {
            Optional<ImageFormat> format = imageContentValidator.detect(file);
            if (format.isEmpty() || format.get() != ImageFormat.PNG) {
                throw TowerFloorException.maskNotPng();
            }
        }
        int[] size = readSize(file);
        String url = storage.store(file, kind);
        log.info(
                "탑 층 이미지 업로드: kind={}, url={}, size={}x{}",
                kind,
                url,
                size == null ? null : size[0],
                size == null ? null : size[1]);
        return new TowerFloorImageUploadResponse(
                kind, url, size == null ? null : size[0], size == null ? null : size[1]);
    }

    /** 픽셀을 디코딩하지 않고 헤더만 읽는다. 리더가 없는 형식(webp)이나 읽기 실패는 null. */
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
                    "탑 층 이미지 크기 읽기 실패: file={}, error={}",
                    file.getOriginalFilename(),
                    e.getMessage());
            return null;
        }
    }
}
