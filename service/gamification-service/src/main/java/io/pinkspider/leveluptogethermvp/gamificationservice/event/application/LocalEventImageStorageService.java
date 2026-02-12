package io.pinkspider.leveluptogethermvp.gamificationservice.event.application;

import io.pinkspider.global.exception.CustomException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 파일 시스템에 이벤트 이미지를 저장하는 구현체
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LocalEventImageStorageService implements EventImageStorageService {

    private final EventImageProperties properties;

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("EVENT_IMAGE_001", "업로드할 이미지 파일이 없습니다.");
        }

        if (!isValidImage(file)) {
            throw new CustomException("EVENT_IMAGE_002", "유효하지 않은 이미지 파일입니다.");
        }

        try {
            // 저장 디렉토리 생성
            Path uploadDir = Paths.get(properties.getPath());
            Files.createDirectories(uploadDir);

            // 파일 이름 생성 (UUID + 확장자)
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String extension = getExtension(originalFilename);
            String newFilename = UUID.randomUUID().toString() + "." + extension;

            // 파일 저장
            Path targetPath = uploadDir.resolve(newFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("이벤트 이미지 저장: path={}", targetPath);

            // URL 반환 (서버 상대 경로)
            return properties.getUrlPrefix() + "/" + newFilename;

        } catch (IOException e) {
            log.error("이벤트 이미지 저장 실패", e);
            throw new CustomException("EVENT_IMAGE_003", "이미지 저장에 실패했습니다.");
        }
    }

    @Override
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        // 외부 URL은 삭제하지 않음
        if (!imageUrl.startsWith(properties.getUrlPrefix())) {
            log.debug("외부 이미지 URL은 삭제하지 않음: {}", imageUrl);
            return;
        }

        try {
            // URL에서 파일 경로 추출
            String relativePath = imageUrl.substring(properties.getUrlPrefix().length());
            Path filePath = Paths.get(properties.getPath(), relativePath);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("이벤트 이미지 삭제: path={}", filePath);
            }
        } catch (IOException e) {
            log.warn("이벤트 이미지 삭제 실패: url={}", imageUrl, e);
        }
    }

    @Override
    public boolean isValidImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        // 파일 크기 검사
        if (file.getSize() > properties.getMaxSize()) {
            log.warn("파일 크기 초과: size={}, maxSize={}", file.getSize(), properties.getMaxSize());
            return false;
        }

        // 확장자 검사
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        String extension = getExtension(filename).toLowerCase();
        List<String> allowedExtensions = properties.getAllowedExtensionList();
        if (!allowedExtensions.contains(extension)) {
            log.warn("허용되지 않은 확장자: extension={}, allowed={}", extension, allowedExtensions);
            return false;
        }

        // MIME 타입 검사
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("유효하지 않은 MIME 타입: contentType={}", contentType);
            return false;
        }

        return true;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
