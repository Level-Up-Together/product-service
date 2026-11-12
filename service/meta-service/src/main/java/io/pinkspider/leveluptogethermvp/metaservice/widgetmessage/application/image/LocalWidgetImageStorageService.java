package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.image;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageImageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * LUT-797: 로컬 파일시스템 저장 (prod 외). {@code {path}/{uuid}.{ext}} 에 두고 {@code WidgetImageWebConfig} 가 정적
 * 서빙한다.
 */
@Service
@Profile("!prod")
@Slf4j
@RequiredArgsConstructor
public class LocalWidgetImageStorageService implements WidgetImageStorageService {
    private final WidgetImageProperties properties;

    @Override
    public String store(MultipartFile file) {
        String extension = WidgetImageFiles.validatedExtension(file, properties);
        String filename = WidgetImageFiles.newFilename(extension);
        try {
            Path dir = Paths.get(properties.getPath());
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String url = ownedPrefix() + filename;
            if (baseUrl().isEmpty()) {
                log.warn(
                        "app.upload.widget-image.public-base-url 이 비어 있어 상대 경로를 돌려준다 — 위젯은 절대 주소만"
                                + " 받을 수 있다: {}",
                        url);
            }
            log.info("위젯 배경 이미지 저장: path={}", target);
            return url;
        } catch (IOException e) {
            log.error("위젯 배경 이미지 저장 실패", e);
            throw WidgetMessageImageException.saveFailed();
        }
    }

    @Override
    public boolean isOwned(String url) {
        return url != null && url.startsWith(ownedPrefix());
    }

    @Override
    public void delete(String url) {
        if (!isOwned(url)) {
            log.debug("위젯 배경 이미지 삭제 대상 아님 (외부 URL): {}", url);
            return;
        }
        String filename = url.substring(ownedPrefix().length());
        // 저장 규칙은 {uuid.ext} 한 단계 — 그 밖(상위 경로·하위 폴더)은 지우지 않는다
        if (filename.isEmpty()
                || filename.contains("/")
                || filename.contains("..")
                || filename.contains("\\")) {
            log.warn("위젯 배경 이미지 업로드 루트 밖 삭제 요청 거부: url={}", url);
            return;
        }
        Path root = Paths.get(properties.getPath()).toAbsolutePath().normalize();
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            log.warn("위젯 배경 이미지 업로드 루트 밖 삭제 요청 거부: path={}", target);
            return;
        }
        try {
            if (Files.deleteIfExists(target)) {
                log.info("위젯 배경 이미지 삭제: path={}", target);
            }
        } catch (IOException e) {
            log.warn("위젯 배경 이미지 삭제 실패: path={}", target, e);
        }
    }

    private String ownedPrefix() {
        return baseUrl() + properties.getUrlPrefix() + "/";
    }

    private String baseUrl() {
        String base = properties.getPublicBaseUrl();
        if (base == null) {
            return "";
        }
        base = base.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
