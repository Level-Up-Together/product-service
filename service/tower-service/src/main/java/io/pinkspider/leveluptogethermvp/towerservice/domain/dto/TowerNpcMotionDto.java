package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcMotion;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcMotionPlayMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LUT-864: NPC 움직이는 그림(스프라이트 시트) — 어드민 요청·응답, 입장 스냅샷 공용. 앱(웹 LUT-865)은 이 값이 있으면 원형 썸네일 대신 시트에서 장을
 * 잘라 전신으로 그린다(칸의 발이 NPC 자리). 탑은 Canvas 2D 라 움직이는 WebP/GIF 대신 시트 한 장 + 프레임 정보로 장을 넘긴다.
 *
 * <p>{@code sheet_url} = 층 이미지 업로드 {@code kind=NPC_MOTION} 이 발급한 주소(PNG·WebP, 5MB — 아니면 150417. 앱이
 * crossOrigin 으로 불러오므로 다른 주소는 못 쓸 수 있다) · {@code frames} = 재생 순서대로 칸 자리·한 장 시간(1~120장) · {@code
 * foot_x}·{@code foot_y}·{@code content_top} = 칸 안 발 가운데·발·그림 맨 위(px, 모든 장 공통, {@code 0 ≤ foot_x ≤
 * 칸 폭}, {@code content_top < foot_y ≤ 칸 높이} — 아니면 150416) · {@code height} = 화면 그림 키(아바타 좌표 px
 * 24~200, 썸네일 40, 비우면 64) · {@code flip} = 좌우 뒤집기(비우면 false) · {@code play_mode} = LOOP /
 * OCCASIONAL(비우면 OCCASIONAL) · {@code interval_seconds} = OCCASIONAL 쉬는 시간 1~60(비우면 6).
 *
 * <p>수정 요청에서 {@code motion} 이 없으면 그대로(구 어드민 호환), {@code sheet_url} 이 비었으면 지운다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcMotionDto(
        @Size(max = 500)
                @Pattern(
                        regexp = "^$|" + TowerFloorAdminRequest.HTTPS_URL,
                        message = "https URL 만 허용합니다")
                String sheetUrl,
        @Size(max = MAX_FRAMES) List<@Valid @NotNull TowerNpcMotionFrameDto> frames,
        @Min(0) @Max(MAX_SHEET_PX) Integer footX,
        @Min(0) @Max(MAX_SHEET_PX) Integer footY,
        @Min(0) @Max(MAX_SHEET_PX) Integer contentTop,
        @Min(MIN_HEIGHT) @Max(MAX_HEIGHT) Integer height,
        Boolean flip,
        TowerNpcMotionPlayMode playMode,
        @Min(MIN_INTERVAL_SECONDS) @Max(MAX_INTERVAL_SECONDS) Integer intervalSeconds) {

    /** 장 수 상한 — 앱(LUT-865 {@code NPC_MOTION_MAX_FRAMES})·어드민과 같다 */
    public static final int MAX_FRAMES = 120;

    /** 시트 한 변 상한(px) — 칸 자리·발 위치도 이 안 */
    public static final int MAX_SHEET_PX = 4096;

    /** 화면 그림 키(아바타 좌표 px) — 앱 {@code NPC_MOTION_HEIGHT} 와 같다 */
    public static final int MIN_HEIGHT = 24;

    public static final int MAX_HEIGHT = 200;
    public static final int DEFAULT_HEIGHT = 64;

    /** OCCASIONAL 쉬는 시간(초) — 앱 {@code NPC_MOTION_INTERVAL} 과 같다 */
    public static final int MIN_INTERVAL_SECONDS = 1;

    public static final int MAX_INTERVAL_SECONDS = 60;
    public static final int DEFAULT_INTERVAL_SECONDS = 6;

    private static final Logger log = LoggerFactory.getLogger(TowerNpcMotionDto.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<TowerNpcMotionFrameDto>> FRAMES =
            new TypeReference<>() {};

    /** 시트 주소가 비었으면 지우라는 뜻 (응답 필드가 아니다) */
    @JsonIgnore
    public boolean isRemoval() {
        return sheetUrl == null || sheetUrl.isBlank();
    }

    /** 지우기면 null. 비운 키·뒤집기·재생 방식·쉬는 시간은 기본값 */
    public TowerNpcMotion toEntityOrNull() {
        if (isRemoval()) {
            return null;
        }
        String framesJson;
        try {
            framesJson = JSON.writeValueAsString(frames == null ? List.of() : frames);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("움직이는 그림 프레임 직렬화 실패", e);
        }
        return TowerNpcMotion.builder()
                .sheetUrl(sheetUrl.trim())
                .frames(framesJson)
                .footX(footX)
                .footY(footY)
                .contentTop(contentTop)
                .height(height != null ? height : DEFAULT_HEIGHT)
                .flip(Boolean.TRUE.equals(flip))
                .playMode(playMode != null ? playMode : TowerNpcMotionPlayMode.OCCASIONAL)
                .intervalSeconds(
                        intervalSeconds != null ? intervalSeconds : DEFAULT_INTERVAL_SECONDS)
                .build();
    }

    /** 없거나 프레임 JSON 을 못 읽으면 null — 앱은 원형 썸네일로 그린다 */
    public static TowerNpcMotionDto from(TowerNpcMotion motion) {
        if (motion == null || motion.getSheetUrl() == null) {
            return null;
        }
        List<TowerNpcMotionFrameDto> frames;
        try {
            frames = motion.getFrames() == null ? null : JSON.readValue(motion.getFrames(), FRAMES);
        } catch (JsonProcessingException e) {
            log.warn("탑 NPC 움직이는 그림 프레임을 읽지 못함: sheet={}", motion.getSheetUrl(), e);
            return null;
        }
        if (frames == null || frames.isEmpty()) {
            return null;
        }
        return new TowerNpcMotionDto(
                motion.getSheetUrl(),
                frames,
                motion.getFootX(),
                motion.getFootY(),
                motion.getContentTop(),
                motion.getHeight(),
                motion.getFlip(),
                motion.getPlayMode(),
                motion.getIntervalSeconds());
    }
}
