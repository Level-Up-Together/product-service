package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.image;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageImageException;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** LUT-797: 로컬·S3 저장소가 같이 쓰는 파일 검증·이름 규칙. 거절은 400 080203. */
@Slf4j
final class WidgetImageFiles {
    private WidgetImageFiles() {}

    /** 비어 있음·크기 초과·확장자 불허 → 080203. 반환은 소문자 확장자. */
    static String validatedExtension(MultipartFile file, WidgetImageProperties properties) {
        if (file == null || file.isEmpty()) {
            throw WidgetMessageImageException.invalid();
        }
        if (file.getSize() > properties.getMaxSize()) {
            log.warn("위젯 배경 이미지 크기 초과: size={}, max={}", file.getSize(), properties.getMaxSize());
            throw WidgetMessageImageException.invalid();
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw WidgetMessageImageException.invalid();
        }
        String cleaned = StringUtils.cleanPath(name);
        int dot = cleaned.lastIndexOf('.');
        if (dot < 0 || dot == cleaned.length() - 1) {
            throw WidgetMessageImageException.invalid();
        }
        String extension = cleaned.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!properties.getAllowedExtensionList().contains(extension)) {
            log.warn(
                    "위젯 배경 이미지 확장자 불허: extension={}, allowed={}",
                    extension,
                    properties.getAllowedExtensionList());
            throw WidgetMessageImageException.invalid();
        }
        return extension;
    }

    /**
     * 저장할 Content-Type — 클라이언트가 보낸 값은 믿지 않고 검증한 확장자에서 정한다(이미지 이름에 다른 형식을 붙여 올려 CDN 이 그 형식으로 서빙하는 것을
     * 막는다).
     */
    static String contentTypeOf(String extension) {
        return "png".equals(extension) ? "image/png" : "image/jpeg";
    }

    /** UUID 파일명 — 내용 불변이라 CDN·기기 캐시를 오래 써도 된다(바꾸면 주소가 바뀐다) */
    static String newFilename(String extension) {
        return UUID.randomUUID() + "." + extension;
    }
}
