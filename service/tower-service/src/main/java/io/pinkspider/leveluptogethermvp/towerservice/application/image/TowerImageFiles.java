package io.pinkspider.leveluptogethermvp.towerservice.application.image;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** LUT-635: 로컬·S3 저장소가 같이 쓰는 파일 검증·이름 규칙. 거절은 이미지 400 150110, 배경 음악(LUT-637) 400 150113. */
@Slf4j
final class TowerImageFiles {
    private TowerImageFiles() {}

    /** 비어 있음·크기 초과·확장자 불허 → 이미지 150110 / 배경 음악 150113. 반환은 소문자 확장자. */
    static String validatedExtension(
            MultipartFile file, TowerImageKind kind, TowerImageProperties properties) {
        boolean audio = kind == TowerImageKind.BGM;
        long maxSize = audio ? properties.getAudioMaxSize() : properties.getMaxSize();
        List<String> allowed =
                audio
                        ? properties.getAudioAllowedExtensionList()
                        : properties.getAllowedExtensionList();
        if (file == null || file.isEmpty()) {
            throw invalid(audio);
        }
        if (file.getSize() > maxSize) {
            log.warn("탑 층 파일 크기 초과: kind={}, size={}, max={}", kind, file.getSize(), maxSize);
            throw invalid(audio);
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw invalid(audio);
        }
        String cleaned = StringUtils.cleanPath(name);
        int dot = cleaned.lastIndexOf('.');
        if (dot < 0 || dot == cleaned.length() - 1) {
            throw invalid(audio);
        }
        String extension = cleaned.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!allowed.contains(extension)) {
            log.warn("탑 층 파일 확장자 불허: kind={}, extension={}, allowed={}", kind, extension, allowed);
            throw invalid(audio);
        }
        return extension;
    }

    private static TowerFloorException invalid(boolean audio) {
        return audio ? TowerFloorException.audioInvalid() : TowerFloorException.imageInvalid();
    }

    /** UUID 파일명 — 내용 불변이라 CDN·브라우저 공격적 캐시를 허용한다 */
    /** LUT-727: 서버가 지은 이름만 받는다 — 영문·숫자·점·하이픈, 경로 문자 금지 */
    static String safeFilename(String filename) {
        if (filename == null || !filename.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
            throw new IllegalArgumentException("invalid generated filename: " + filename);
        }
        return filename;
    }

    static String newFilename(String extension) {
        return UUID.randomUUID() + "." + extension;
    }
}
