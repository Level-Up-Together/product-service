package io.pinkspider.leveluptogethermvp.chatservice.application;

import io.pinkspider.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 채팅 이미지 파일 검증·파일명 유틸 — 로컬/S3 구현체 공용 (LUT-590) */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatImageFileValidator {

    static final String THUMB_SUFFIX = "_thumb";
    static final String MEDIUM_SUFFIX = "_medium";

    private final ChatImageProperties properties;

    /** 비었거나 형식·크기가 맞지 않으면 예외. 통과하면 소문자 확장자를 돌려준다. */
    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("CHAT_IMAGE_001", "error.image.empty");
        }
        if (file.getSize() > properties.getMaxSize()) {
            log.warn("채팅 이미지 크기 초과: size={}, max={}", file.getSize(), properties.getMaxSize());
            throw new CustomException("CHAT_IMAGE_002", "error.image.invalid");
        }
        String extension = getExtension(file.getOriginalFilename()).toLowerCase();
        if (!properties.getAllowedExtensionList().contains(extension)) {
            log.warn("채팅 이미지 확장자 불가: extension={}", extension);
            throw new CustomException("CHAT_IMAGE_002", "error.image.invalid");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("채팅 이미지 MIME 불가: contentType={}", contentType);
            throw new CustomException("CHAT_IMAGE_002", "error.image.invalid");
        }
        return extension;
    }

    /**
     * 검증을 통과한 확장자의 고정 MIME — 저장 시 클라이언트 Content-Type 을 쓰지 않기 위함. {@code photo.jpg} 에 {@code
     * image/svg+xml} 을 붙여 올리면 CDN 이 SVG 로 서빙해 스크립트가 실행될 수 있다.
     */
    static String contentTypeOf(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> throw new CustomException("CHAT_IMAGE_002", "error.image.invalid");
        };
    }

    static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    static String insertSuffix(String path, String suffix) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0) {
            return path + suffix;
        }
        return path.substring(0, dotIndex) + suffix + path.substring(dotIndex);
    }
}
