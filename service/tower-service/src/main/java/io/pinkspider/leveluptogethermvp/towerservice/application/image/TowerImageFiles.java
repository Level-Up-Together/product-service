package io.pinkspider.leveluptogethermvp.towerservice.application.image;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** LUT-635: 로컬·S3 저장소가 같이 쓰는 파일 검증·이름 규칙. 거절은 전부 400 150110. */
@Slf4j
final class TowerImageFiles {
    private TowerImageFiles() {}

    /** 비어 있음·크기 초과·확장자 불허 → 150110. 반환은 소문자 확장자. */
    static String validatedExtension(MultipartFile file, TowerImageProperties properties) {
        if (file == null || file.isEmpty()) {
            throw TowerFloorException.imageInvalid();
        }
        if (file.getSize() > properties.getMaxSize()) {
            log.warn("탑 층 이미지 크기 초과: size={}, max={}", file.getSize(), properties.getMaxSize());
            throw TowerFloorException.imageInvalid();
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw TowerFloorException.imageInvalid();
        }
        String cleaned = StringUtils.cleanPath(name);
        int dot = cleaned.lastIndexOf('.');
        if (dot < 0 || dot == cleaned.length() - 1) {
            throw TowerFloorException.imageInvalid();
        }
        String extension = cleaned.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!properties.getAllowedExtensionList().contains(extension)) {
            log.warn(
                    "탑 층 이미지 확장자 불허: extension={}, allowed={}",
                    extension,
                    properties.getAllowedExtensions());
            throw TowerFloorException.imageInvalid();
        }
        return extension;
    }

    /** UUID 파일명 — 내용 불변이라 CDN·브라우저 공격적 캐시를 허용한다 */
    static String newFilename(String extension) {
        return UUID.randomUUID() + "." + extension;
    }
}
