package io.pinkspider.global.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageResizerTest {

    private final ImageResizer imageResizer = new ImageResizer();

    private byte[] createImage(int width, int height, String formatName) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, formatName, outputStream);
        return outputStream.toByteArray();
    }

    @Test
    @DisplayName("원본이 목표 크기보다 크면 리사이즈된 이미지를 반환한다")
    void resize_largerThanTarget_returnsResizedImage() throws IOException {
        byte[] original = createImage(1000, 800, "png");

        Optional<byte[]> result =
                imageResizer.resize(original, "png", ImageResizer.THUMBNAIL_MAX_DIMENSION);

        assertThat(result).isPresent();
        BufferedImage resized = ImageIO.read(new ByteArrayInputStream(result.get()));
        assertThat(Math.max(resized.getWidth(), resized.getHeight()))
                .isLessThanOrEqualTo(ImageResizer.THUMBNAIL_MAX_DIMENSION);
    }

    @Test
    @DisplayName("리사이즈 시 원본 비율을 유지한다")
    void resize_preservesAspectRatio() throws IOException {
        byte[] original = createImage(2000, 1000, "png"); // 2:1

        Optional<byte[]> result =
                imageResizer.resize(original, "png", ImageResizer.MEDIUM_MAX_DIMENSION);

        assertThat(result).isPresent();
        BufferedImage resized = ImageIO.read(new ByteArrayInputStream(result.get()));
        double originalRatio = 2000.0 / 1000.0;
        double resizedRatio = (double) resized.getWidth() / resized.getHeight();
        assertThat(resizedRatio)
                .isCloseTo(originalRatio, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    @DisplayName("원본이 목표 크기보다 작거나 같으면 변형을 생성하지 않는다")
    void resize_smallerThanOrEqualToTarget_returnsEmpty() throws IOException {
        byte[] original = createImage(300, 200, "png");

        Optional<byte[]> result =
                imageResizer.resize(original, "png", ImageResizer.THUMBNAIL_MAX_DIMENSION);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("디코딩할 수 없는 바이트는 변형을 생성하지 않는다")
    void resize_undecodableBytes_returnsEmpty() {
        byte[] garbage = new byte[] {1, 2, 3, 4, 5};

        Optional<byte[]> result =
                imageResizer.resize(garbage, "jpg", ImageResizer.THUMBNAIL_MAX_DIMENSION);

        assertThat(result).isEmpty();
    }
}
