package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.image.ImageContentValidator;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.image.WidgetImageStorageService;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageImageUploadResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("WidgetMessageImageService — 위젯 배경 이미지 업로드 (LUT-797)")
class WidgetMessageImageServiceTest {

    private static final String URL = "https://cdn.test/widget/a.png";

    @Mock WidgetImageStorageService storage;

    private WidgetMessageImageService service;

    @BeforeEach
    void setUp() {
        // 형식 판정은 실제 검사기로 — 확장자·Content-Type 이 아니라 내용을 본다
        service = new WidgetMessageImageService(storage, new ImageContentValidator());
    }

    private static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("PNG — 저장하고 주소·크기·바이트 수를 돌려준다")
    void png() throws IOException {
        byte[] bytes = image("png", 64, 48);
        MockMultipartFile file = new MockMultipartFile("file", "bg.png", "image/png", bytes);
        when(storage.store(file)).thenReturn(URL);

        WidgetMessageImageUploadResponse response = service.upload(file);

        assertThat(response)
                .isEqualTo(new WidgetMessageImageUploadResponse(URL, 64, 48, bytes.length));
    }

    @Test
    @DisplayName("JPG 도 받는다")
    void jpeg() throws IOException {
        MockMultipartFile file =
                new MockMultipartFile("file", "bg.jpg", "image/jpeg", image("jpg", 32, 32));
        when(storage.store(file)).thenReturn(URL);

        assertThat(service.upload(file).width()).isEqualTo(32);
    }

    @Test
    @DisplayName("파일이 없거나 비어 있으면 080203")
    void empty() {
        assertInvalid(null);
        assertInvalid(new MockMultipartFile("file", "bg.png", "image/png", new byte[0]));
        verify(storage, never()).store(any());
    }

    @Test
    @DisplayName("확장자가 png 여도 내용이 이미지가 아니면 080203")
    void notAnImage() {
        assertInvalid(
                new MockMultipartFile("file", "bg.png", "image/png", "hello world".getBytes()));
        verify(storage, never()).store(any());
    }

    @Test
    @DisplayName("JPG·PNG 가 아닌 이미지(GIF)는 080203 — 위젯이 바로 그릴 수 있는 형식만")
    void gif() throws IOException {
        assertInvalid(new MockMultipartFile("file", "bg.png", "image/png", image("gif", 8, 8)));
        verify(storage, never()).store(any());
    }

    @Test
    @DisplayName("헤더는 PNG 인데 크기를 못 읽으면 크기 없이 저장한다 (업로드는 막지 않는다)")
    void sizeUnreadable() {
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "bg.png", "image/png", header);
        when(storage.store(file)).thenReturn(URL);

        WidgetMessageImageUploadResponse response = service.upload(file);

        assertThat(response.url()).isEqualTo(URL);
        assertThat(response.width()).isNull();
        assertThat(response.height()).isNull();
    }

    @Test
    @DisplayName("readSize — 스트림을 못 열면 null")
    void readSizeIoFailure() throws IOException {
        MultipartFile broken = org.mockito.Mockito.mock(MultipartFile.class);
        when(broken.getInputStream()).thenThrow(new IOException("disk"));

        assertThat(WidgetMessageImageService.readSize(broken)).isNull();
    }

    @Test
    @DisplayName("readSize — 이미지 리더가 없는 내용이면 null")
    void readSizeNoReader() throws IOException {
        MultipartFile text = org.mockito.Mockito.mock(MultipartFile.class);
        when(text.getInputStream()).thenReturn(InputStream.nullInputStream());

        assertThat(WidgetMessageImageService.readSize(text)).isNull();
    }

    private void assertInvalid(MultipartFile file) {
        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOfSatisfying(
                        WidgetMessageImageException.class,
                        e ->
                                assertThat(e.getCode())
                                        .isEqualTo(WidgetMessageImageException.CODE_INVALID));
    }
}
