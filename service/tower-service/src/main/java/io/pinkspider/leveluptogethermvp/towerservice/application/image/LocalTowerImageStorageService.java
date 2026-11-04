package io.pinkspider.leveluptogethermvp.towerservice.application.image;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;
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
 * LUT-635: 로컬 파일시스템 저장 (prod 외). {@code {path}/{kind}/{uuid}.{ext}} 에 두고 {@code
 * TowerImageWebConfig} 가 정적 서빙한다.
 */
@Service
@Profile("!prod")
@Slf4j
@RequiredArgsConstructor
public class LocalTowerImageStorageService implements TowerImageStorageService {
    private final TowerImageProperties properties;

    @Override
    public String store(MultipartFile file, TowerImageKind kind) {
        String extension = TowerImageFiles.validatedExtension(file, kind, properties);
        String filename = TowerImageFiles.newFilename(extension);
        try {
            Path dir = Paths.get(properties.getPath(), kind.folder());
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String url = ownedPrefix() + kind.folder() + "/" + filename;
            if (baseUrl().isEmpty()) {
                log.warn(
                        "app.upload.tower-image.public-base-url 이 비어 있어 상대 경로를 돌려준다 — 층 API 는 https"
                                + " 절대 주소만 받는다: {}",
                        url);
            }
            log.info("탑 층 이미지 저장: kind={}, path={}", kind, target);
            return url;
        } catch (IOException e) {
            log.error("탑 층 이미지 저장 실패: kind={}", kind, e);
            throw TowerFloorException.imageSaveFailed();
        }
    }

    @Override
    public String storeGenerated(
            byte[] bytes, TowerImageKind kind, String filename, String contentType) {
        String safe = TowerImageFiles.safeFilename(filename);
        try {
            Path dir = Paths.get(properties.getPath(), kind.folder());
            Files.createDirectories(dir);
            Path target = dir.resolve(safe);
            Files.write(target, bytes);
            log.info("탑 생성 파일 저장: kind={}, path={}, size={}", kind, target, bytes.length);
            return ownedPrefix() + kind.folder() + "/" + safe;
        } catch (IOException e) {
            log.error("탑 생성 파일 저장 실패: kind={}", kind, e);
            throw TowerFloorException.imageSaveFailed();
        }
    }

    @Override
    public boolean isOwned(String url) {
        return url != null && url.startsWith(ownedPrefix());
    }

    @Override
    public void delete(String url) {
        if (!isOwned(url)) {
            log.debug("탑 층 이미지 삭제 대상 아님 (외부 URL): {}", url);
            return;
        }
        String relative = url.substring(ownedPrefix().length());
        String[] segments = relative.split("/");
        // 저장 규칙은 {kind}/{uuid.ext} 두 단계 — 그 밖(상위 경로·다른 폴더 깊이)은 지우지 않는다
        if (segments.length != 2
                || segments[0].isEmpty()
                || segments[1].isEmpty()
                || relative.contains("..")
                || relative.contains("\\")) {
            log.warn("탑 층 이미지 업로드 루트 밖 삭제 요청 거부: url={}", url);
            return;
        }
        Path root = Paths.get(properties.getPath()).toAbsolutePath().normalize();
        Path target = root.resolve(segments[0]).resolve(segments[1]).normalize();
        if (!target.startsWith(root)) {
            log.warn("탑 층 이미지 업로드 루트 밖 삭제 요청 거부: path={}", target);
            return;
        }
        try {
            if (Files.deleteIfExists(target)) {
                log.info("탑 층 이미지 삭제: path={}", target);
            }
        } catch (IOException e) {
            log.warn("탑 층 이미지 삭제 실패: path={}", target, e);
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
