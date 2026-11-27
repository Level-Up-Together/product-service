package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcMotion;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcMotionPlayMode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TowerNpcMotionDto (LUT-864)")
class TowerNpcMotionDtoTest {

    private static final String SHEET = "https://cdn.test/tower/npc-motion/cat.webp";

    private static TowerNpcMotionFrameDto frame(int durationMs) {
        return new TowerNpcMotionFrameDto(0, 0, 256, 256, durationMs);
    }

    private static TowerNpcMotionDto motion(
            String sheet,
            List<TowerNpcMotionFrameDto> frames,
            Integer height,
            Integer intervalSeconds) {
        return new TowerNpcMotionDto(
                sheet, frames, 116, 243, 55, height, null, null, intervalSeconds);
    }

    private static Set<String> violations(TowerNpcMotionDto dto) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<TowerNpcMotionDto>> found = validator.validate(dto);
            Set<String> paths = new java.util.TreeSet<>();
            found.forEach(v -> paths.add(v.getPropertyPath().toString()));
            return paths;
        }
    }

    @Test
    @DisplayName("시트 주소가 비었으면(null·빈 문자열·공백) 지우기 — 엔티티 null")
    void removal() {
        for (String blank : new String[] {null, "", "  "}) {
            TowerNpcMotionDto dto = motion(blank, List.of(frame(128)), null, null);
            assertThat(dto.isRemoval()).isTrue();
            assertThat(dto.toEntityOrNull()).isNull();
        }
        assertThat(motion(SHEET, List.of(frame(128)), null, null).isRemoval()).isFalse();
    }

    @Test
    @DisplayName("엔티티 ↔ DTO — 프레임은 snake_case JSON 으로 저장하고 그대로 읽는다")
    void roundTrip() {
        TowerNpcMotionDto dto =
                new TowerNpcMotionDto(
                        SHEET,
                        List.of(frame(128), new TowerNpcMotionFrameDto(256, 0, 256, 256, 96)),
                        116,
                        243,
                        55,
                        80,
                        true,
                        TowerNpcMotionPlayMode.LOOP,
                        3);
        TowerNpcMotion entity = dto.toEntityOrNull();
        assertThat(entity.getFrames())
                .isEqualTo(
                        "[{\"x\":0,\"y\":0,\"w\":256,\"h\":256,\"duration_ms\":128},"
                                + "{\"x\":256,\"y\":0,\"w\":256,\"h\":256,\"duration_ms\":96}]");
        assertThat(TowerNpcMotionDto.from(entity)).isEqualTo(dto);
    }

    @Test
    @DisplayName("없거나 시트가 없거나 프레임 JSON 이 깨졌거나 비었으면 null — 앱은 원형 썸네일")
    void fromBroken() {
        assertThat(TowerNpcMotionDto.from(null)).isNull();
        assertThat(TowerNpcMotionDto.from(TowerNpcMotion.builder().frames("[]").build())).isNull();
        for (String frames : new String[] {null, "[]", "not json", "{\"x\":1}"}) {
            assertThat(
                            TowerNpcMotionDto.from(
                                    TowerNpcMotion.builder()
                                            .sheetUrl(SHEET)
                                            .frames(frames)
                                            .footX(1)
                                            .footY(2)
                                            .contentTop(0)
                                            .build()))
                    .as(frames)
                    .isNull();
        }
    }

    @Test
    @DisplayName("요청 검증 — https 시트·장 120개·키 24~200·쉬는 시간 1~60·한 장 16~5000ms·칸 크기 1 이상")
    void beanValidation() {
        assertThat(violations(motion(SHEET, List.of(frame(16), frame(5000)), 24, 1))).isEmpty();
        assertThat(violations(motion("", null, 200, 60))).isEmpty();
        assertThat(violations(motion("http://cdn/cat.webp", List.of(frame(128)), null, null)))
                .containsExactly("sheetUrl");
        List<TowerNpcMotionFrameDto> many = new ArrayList<>();
        for (int i = 0; i < TowerNpcMotionDto.MAX_FRAMES + 1; i++) {
            many.add(frame(128));
        }
        assertThat(violations(motion(SHEET, many, null, null))).containsExactly("frames");
        assertThat(violations(motion(SHEET, List.of(frame(128)), 23, null)))
                .containsExactly("height");
        assertThat(violations(motion(SHEET, List.of(frame(128)), 201, null)))
                .containsExactly("height");
        assertThat(violations(motion(SHEET, List.of(frame(128)), null, 0)))
                .containsExactly("intervalSeconds");
        assertThat(violations(motion(SHEET, List.of(frame(128)), null, 61)))
                .containsExactly("intervalSeconds");
        assertThat(violations(motion(SHEET, List.of(frame(15)), null, null)))
                .containsExactly("frames[0].durationMs");
        assertThat(violations(motion(SHEET, List.of(frame(5001)), null, null)))
                .containsExactly("frames[0].durationMs");
        assertThat(
                        violations(
                                motion(
                                        SHEET,
                                        List.of(new TowerNpcMotionFrameDto(0, 0, 0, 256, 128)),
                                        null,
                                        null)))
                .containsExactly("frames[0].w");
    }
}
