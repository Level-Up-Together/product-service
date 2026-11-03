package io.pinkspider.leveluptogethermvp.chatservice.application;

import static io.pinkspider.leveluptogethermvp.chatservice.application.ChatImageFileValidator.MEDIUM_SUFFIX;
import static io.pinkspider.leveluptogethermvp.chatservice.application.ChatImageFileValidator.THUMB_SUFFIX;
import static io.pinkspider.leveluptogethermvp.chatservice.application.ChatImageFileValidator.insertSuffix;

import io.pinkspider.global.component.ImageResizer;
import io.pinkspider.global.exception.CustomException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 로컬 파일 시스템 채팅 이미지 저장소 (dev/local, LUT-590). 경로: {path}/{guildId}/{userId}/{uuid}.{ext} */
@Service
@Profile("!prod")
@Slf4j
@RequiredArgsConstructor
public class LocalChatImageStorageService implements ChatImageStorageService {

    private final ChatImageProperties properties;
    private final ChatImageFileValidator validator;
    private final ImageResizer imageResizer;

    @Override
    public String store(MultipartFile file, Long guildId, String userId) {
        String extension = validator.validate(file);
        try {
            Path uploadDir = Paths.get(properties.getPath(), String.valueOf(guildId), userId);
            Files.createDirectories(uploadDir);

            String filename = UUID.randomUUID() + "." + extension;
            byte[] originalBytes = file.getBytes();
            Files.write(uploadDir.resolve(filename), originalBytes);

            writeVariant(
                    uploadDir,
                    filename,
                    extension,
                    originalBytes,
                    THUMB_SUFFIX,
                    ImageResizer.THUMBNAIL_MAX_DIMENSION);
            writeVariant(
                    uploadDir,
                    filename,
                    extension,
                    originalBytes,
                    MEDIUM_SUFFIX,
                    ImageResizer.MEDIUM_MAX_DIMENSION);

            log.info("채팅 이미지 저장: guildId={}, userId={}, file={}", guildId, userId, filename);
            return properties.getUrlPrefix() + "/" + guildId + "/" + userId + "/" + filename;
        } catch (IOException e) {
            log.error("채팅 이미지 저장 실패: guildId={}, userId={}", guildId, userId, e);
            throw new CustomException("CHAT_IMAGE_003", "error.image.save_failed");
        }
    }

    /** 변형은 best-effort — 실패하거나 리사이즈 불가 포맷(GIF 등)이면 원본만 남고 클라이언트가 원본으로 폴백한다. */
    private void writeVariant(
            Path uploadDir,
            String filename,
            String extension,
            byte[] originalBytes,
            String suffix,
            int maxDimension) {
        try {
            Optional<byte[]> resized = imageResizer.resize(originalBytes, extension, maxDimension);
            if (resized.isPresent()) {
                Files.write(uploadDir.resolve(insertSuffix(filename, suffix)), resized.get());
            }
        } catch (Exception e) {
            log.warn("채팅 이미지 변형 저장 실패, 원본만 사용: file={}, suffix={}", filename, suffix, e);
        }
    }

    @Override
    public void delete(String imageUrl) {
        if (!isStoredUrl(imageUrl)) {
            return;
        }
        String relativePath = imageUrl.substring(properties.getUrlPrefix().length());
        deleteIfExists(relativePath);
        deleteIfExists(insertSuffix(relativePath, THUMB_SUFFIX));
        deleteIfExists(insertSuffix(relativePath, MEDIUM_SUFFIX));
    }

    private void deleteIfExists(String relativePath) {
        try {
            Files.deleteIfExists(Paths.get(properties.getPath(), relativePath));
        } catch (IOException e) {
            log.warn("채팅 이미지 삭제 실패: path={}", relativePath, e);
        }
    }

    @Override
    public boolean isOwnedBy(String imageUrl, Long guildId, String userId) {
        return guildId != null
                && userId != null
                && isStoredUrl(imageUrl)
                && imageUrl.startsWith(
                        properties.getUrlPrefix() + "/" + guildId + "/" + userId + "/");
    }

    /** 이 저장소의 채팅 이미지 URL 인지 (상위 경로 {@code ..} 거부) — 삭제 대상 한정용 */
    private boolean isStoredUrl(String imageUrl) {
        return imageUrl != null
                && imageUrl.startsWith(properties.getUrlPrefix() + "/")
                && !imageUrl.contains("..");
    }
}
