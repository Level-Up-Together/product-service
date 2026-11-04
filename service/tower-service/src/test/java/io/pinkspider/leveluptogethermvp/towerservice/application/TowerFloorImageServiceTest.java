package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.image.ImageContentValidator;
import io.pinkspider.global.image.InvalidImageException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.image.TowerImageStorageService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorImageUploadResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("TowerFloorImageService (LUT-635)")
class TowerFloorImageServiceTest {
    @Mock private TowerImageStorageService storage;
    @Spy private ImageContentValidator imageContentValidator = new ImageContentValidator();
    @InjectMocks private TowerFloorImageService service;

    private static byte[] png(int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpeg(int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("배경 JPEG: 형식 검증 → 저장 → 크기(헤더) 를 응답에 싣는다")
    void uploadBackground() throws IOException {
        MockMultipartFile file =
                new MockMultipartFile("file", "bg.jpg", "image/jpeg", jpeg(40, 30));
        when(storage.store(file, TowerImageKind.BACKGROUND))
                .thenReturn("https://cdn/tower/background/x.jpg");

        TowerFloorImageUploadResponse res = service.upload(file, TowerImageKind.BACKGROUND);

        assertThat(res.kind()).isEqualTo(TowerImageKind.BACKGROUND);
        assertThat(res.url()).isEqualTo("https://cdn/tower/background/x.jpg");
        assertThat(res.width()).isEqualTo(40);
        assertThat(res.height()).isEqualTo(30);
    }

    @Test
    @DisplayName("마스크 PNG 는 통과, 마스크 JPEG 는 150112(저장 안 함)")
    void maskMustBePng() throws IOException {
        MockMultipartFile mask = new MockMultipartFile("file", "mask.png", "image/png", png(8, 4));
        when(storage.store(mask, TowerImageKind.MASK)).thenReturn("https://cdn/tower/mask/m.png");
        TowerFloorImageUploadResponse res = service.upload(mask, TowerImageKind.MASK);
        assertThat(res.width()).isEqualTo(8);
        assertThat(res.height()).isEqualTo(4);

        MockMultipartFile jpegMask =
                new MockMultipartFile("file", "mask.jpg", "image/jpeg", jpeg(8, 4));
        assertThatThrownBy(() -> service.upload(jpegMask, TowerImageKind.MASK))
                .isInstanceOf(TowerFloorException.class)
                .hasFieldOrPropertyWithValue("code", TowerFloorException.MASK_NOT_PNG);
        verify(storage, never()).store(eq(jpegMask), any());
    }

    @Test
    @DisplayName("빈 파일 150110 · 이미지 아닌 바이트는 LUT-710 InvalidImageException(400)")
    void rejects() {
        assertThatThrownBy(
                        () ->
                                service.upload(
                                        new MockMultipartFile(
                                                "file", "a.png", "image/png", new byte[0]),
                                        TowerImageKind.BACKGROUND))
                .hasFieldOrPropertyWithValue("code", TowerFloorException.IMAGE_INVALID);
        assertThatThrownBy(
                        () ->
                                service.upload(
                                        new MockMultipartFile(
                                                "file",
                                                "a.png",
                                                "image/png",
                                                "not an image".getBytes()),
                                        TowerImageKind.BACKGROUND))
                .isInstanceOf(InvalidImageException.class);
        assertThatThrownBy(() -> service.upload(null, TowerImageKind.BACKGROUND))
                .hasFieldOrPropertyWithValue("code", TowerFloorException.IMAGE_INVALID);
    }

    @Test
    @DisplayName("리더가 없는 형식(webp 헤더)은 크기 없이 저장한다")
    void sizeUnknown() {
        byte[] webp = new byte[32];
        byte[] riff = "RIFF....WEBPVP8 ".getBytes();
        System.arraycopy(riff, 0, webp, 0, riff.length);
        MockMultipartFile file = new MockMultipartFile("file", "bg.webp", "image/webp", webp);
        when(storage.store(file, TowerImageKind.BACKGROUND))
                .thenReturn("https://cdn/tower/background/w.webp");

        TowerFloorImageUploadResponse res = service.upload(file, TowerImageKind.BACKGROUND);

        assertThat(res.width()).isNull();
        assertThat(res.height()).isNull();
        assertThat(res.url()).endsWith("w.webp");
    }

    @Test
    @DisplayName("LUT-637: kind=BGM 은 이미지 업로드로 받지 않는다 (150110, 저장 안 함)")
    void bgmKindRejected() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", png(4, 4));
        assertThatThrownBy(() -> service.upload(file, TowerImageKind.BGM))
                .isInstanceOf(TowerFloorException.class)
                .extracting("code")
                .isEqualTo(TowerFloorException.IMAGE_INVALID);
        // LUT-727: 픽셀 배경은 서버가 만든다
        assertThatThrownBy(() -> service.upload(file, TowerImageKind.PIXEL))
                .isInstanceOf(TowerFloorException.class)
                .extracting("code")
                .isEqualTo(TowerFloorException.IMAGE_INVALID);
        verify(storage, never()).store(any(), any());
    }
}
