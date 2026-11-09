package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.image.TowerImageStorageService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAudioUploadResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * LUT-637: 어드민 층 배경 음악 업로드. 확장자·크기(10MB)는 저장소가, 내용은 여기서 헤더로 본다 — MP3(ID3 태그 또는 MPEG 프레임 동기) · AAC
 * ADTS · M4A(mp4 컨테이너 {@code ftyp}). 확장자만 바꾼 다른 파일은 150113. 같은 저장소({@code tower/bgm/})에 두고 CDN 절대
 * 주소를 돌려준다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TowerFloorAudioService {

    private static final byte[] FTYP = "ftyp".getBytes(StandardCharsets.US_ASCII);

    private final TowerImageStorageService storage;

    public TowerFloorAudioUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw TowerFloorException.audioInvalid();
        }
        if (!looksLikeAudio(file)) {
            log.warn("탑 층 배경 음악 형식 불일치: file={}", file.getOriginalFilename());
            throw TowerFloorException.audioInvalid();
        }
        String url = storage.store(file, TowerImageKind.BGM);
        log.info("탑 층 배경 음악 업로드: url={}, size={}", url, file.getSize());
        return new TowerFloorAudioUploadResponse(url, file.getSize(), file.getContentType());
    }

    /** 처음 12바이트로 MP3(ID3·프레임 동기)·AAC ADTS·M4A(ftyp) 를 가린다. 못 읽으면 false. */
    static boolean looksLikeAudio(MultipartFile file) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return false;
        }
        if (read < 4) return false;
        if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') return true; // MP3 ID3v2
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) return true; // MPEG 프레임 동기 / ADTS
        if (read >= 8
                && head[4] == FTYP[0]
                && head[5] == FTYP[1]
                && head[6] == FTYP[2]
                && head[7] == FTYP[3]) {
            return true; // M4A (mp4)
        }
        return false;
    }
}
