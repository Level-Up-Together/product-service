package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.image.TowerImageStorageService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAudioUploadResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("TowerFloorAudioService (LUT-637)")
class TowerFloorAudioServiceTest {

    private final TowerImageStorageService storage = mock(TowerImageStorageService.class);
    private final TowerFloorAudioService service = new TowerFloorAudioService(storage);

    private static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "audio/mpeg", bytes);
    }

    private static void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run)
                .isInstanceOf(TowerFloorException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    @Test
    @DisplayName("MP3(ID3) 를 BGM 저장소에 두고 url·크기·Content-Type 을 돌려준다")
    void uploadMp3() {
        MockMultipartFile mp3 = file("loop.mp3", new byte[] {'I', 'D', '3', 4, 0, 0, 0, 0});
        when(storage.store(mp3, TowerImageKind.BGM)).thenReturn("https://cdn/tower/bgm/a.mp3");

        TowerFloorAudioUploadResponse res = service.upload(mp3);

        assertThat(res.url()).isEqualTo("https://cdn/tower/bgm/a.mp3");
        assertThat(res.sizeBytes()).isEqualTo(8L);
        assertThat(res.contentType()).isEqualTo("audio/mpeg");
    }

    @Test
    @DisplayName("헤더 판정: ID3 · MPEG/ADTS 프레임 동기 · M4A(ftyp) 는 오디오, 그 밖·4바이트 미만·읽기 실패는 아님")
    void looksLikeAudio() throws IOException {
        assertThat(
                        TowerFloorAudioService.looksLikeAudio(
                                file("a.mp3", new byte[] {'I', 'D', '3', 3})))
                .isTrue();
        assertThat(
                        TowerFloorAudioService.looksLikeAudio(
                                file("a.mp3", new byte[] {(byte) 0xFF, (byte) 0xFB, 0, 0})))
                .isTrue();
        assertThat(
                        TowerFloorAudioService.looksLikeAudio(
                                file("a.aac", new byte[] {(byte) 0xFF, (byte) 0xF1, 0, 0})))
                .isTrue();
        assertThat(
                        TowerFloorAudioService.looksLikeAudio(
                                file(
                                        "a.m4a",
                                        new byte[] {
                                            0, 0, 0, 32, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '
                                        })))
                .isTrue();
        // PNG 를 .mp3 로 바꾼 파일
        assertThat(
                        TowerFloorAudioService.looksLikeAudio(
                                file(
                                        "fake.mp3",
                                        new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})))
                .isFalse();
        assertThat(TowerFloorAudioService.looksLikeAudio(file("short.mp3", new byte[] {'I', 'D'})))
                .isFalse();

        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getInputStream()).thenThrow(new IOException("gone"));
        assertThat(TowerFloorAudioService.looksLikeAudio(broken)).isFalse();
    }

    @Test
    @DisplayName("빈 파일·null·오디오가 아닌 내용은 150113 이고 저장하지 않는다")
    void rejects() {
        assertCode(() -> service.upload(null), TowerFloorException.AUDIO_INVALID);
        assertCode(
                () -> service.upload(file("empty.mp3", new byte[0])),
                TowerFloorException.AUDIO_INVALID);
        assertCode(
                () -> service.upload(file("fake.mp3", new byte[] {1, 2, 3, 4, 5, 6, 7, 8})),
                TowerFloorException.AUDIO_INVALID);
        verify(storage, never()).store(any(), any());
    }
}
