package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerPixelArtStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LUT-727: 층 배경 픽셀아트 (층당 1행). 원본을 등록·교체하거나 어드민이 설정을 바꿔 재생성하면 {@link #request} 로 요청 번호를 올리고, 작업자는 자기
 * 번호가 아직 최신일 때만 결과를 쓴다(늦게 끝난 옛 작업이 새 결과를 덮지 않게). 결과는 {@link #sourceUrl} 이 층의 지금 배경과 같을 때만 쓴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tower_floor_pixel_art")
public class TowerFloorPixelArt extends LocalDateTimeBaseEntity {

    @Id
    @Column(name = "floor_id", nullable = false)
    private Long floorId;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "colors", nullable = false)
    private Integer colors;

    @Column(name = "sharpness", nullable = false)
    private Integer sharpness;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private TowerPixelArtStatus status;

    @Column(name = "request_seq", nullable = false)
    private Long requestSeq;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "url", column = @Column(name = "dense_url", length = 500)),
        @AttributeOverride(name = "width", column = @Column(name = "dense_width")),
        @AttributeOverride(name = "height", column = @Column(name = "dense_height")),
        @AttributeOverride(name = "version", column = @Column(name = "dense_version", length = 32))
    })
    private TowerPixelImage dense;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "url", column = @Column(name = "standard_url", length = 500)),
        @AttributeOverride(name = "width", column = @Column(name = "standard_width")),
        @AttributeOverride(name = "height", column = @Column(name = "standard_height")),
        @AttributeOverride(
                name = "version",
                column = @Column(name = "standard_version", length = 32))
    })
    private TowerPixelImage standard;

    @Column(name = "error_message", length = 300)
    private String errorMessage;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static TowerFloorPixelArt create(
            Long floorId, String sourceUrl, int colors, int sharpness) {
        TowerFloorPixelArt art = new TowerFloorPixelArt();
        art.floorId = floorId;
        art.sourceUrl = sourceUrl;
        art.colors = colors;
        art.sharpness = sharpness;
        art.status = TowerPixelArtStatus.PENDING;
        art.requestSeq = 0L;
        return art;
    }

    /**
     * 새 변환 요청 — 요청 번호를 올리고 대기로 둔다. 원본이 바뀌면 옛 결과는 바로 비운다(새 배경과 구도가 다르다). 같은 원본의 재생성이면 새 결과가 나올 때까지 옛
     * 결과를 그대로 보여 준다. 설정은 넘긴 것만 바꾼다.
     *
     * @return 이번 요청 번호
     */
    public long request(String source, Integer colors, Integer sharpness, LocalDateTime now) {
        if (!Objects.equals(this.sourceUrl, source)) {
            this.dense = null;
            this.standard = null;
            this.completedAt = null;
        }
        this.sourceUrl = source;
        if (colors != null) {
            this.colors = colors;
        }
        if (sharpness != null) {
            this.sharpness = sharpness;
        }
        this.status = TowerPixelArtStatus.PENDING;
        this.errorMessage = null;
        this.requestedAt = now;
        this.requestSeq = this.requestSeq + 1;
        return this.requestSeq;
    }

    public boolean isCurrent(long seq) {
        return requestSeq != null && requestSeq == seq;
    }

    /** 결과 기록 — 요청 번호가 지나갔으면 false(아무것도 안 바꾼다) */
    public boolean complete(
            long seq, TowerPixelImage dense, TowerPixelImage standard, LocalDateTime now) {
        if (!isCurrent(seq)) {
            return false;
        }
        this.dense = dense;
        this.standard = standard;
        this.status = TowerPixelArtStatus.DONE;
        this.errorMessage = null;
        this.completedAt = now;
        return true;
    }

    /** 실패 기록 — 결과는 비운다(프론트는 원본으로 보여 준다). 요청 번호가 지나갔으면 false */
    public boolean fail(long seq, String message, LocalDateTime now) {
        if (!isCurrent(seq)) {
            return false;
        }
        this.dense = null;
        this.standard = null;
        this.status = TowerPixelArtStatus.FAILED;
        this.errorMessage =
                message == null ? null : message.substring(0, Math.min(300, message.length()));
        this.completedAt = now;
        return true;
    }

    /** 이 배경으로 만든 완료 결과가 있는가 */
    public boolean isReadyFor(String backgroundUrl) {
        return status == TowerPixelArtStatus.DONE && Objects.equals(sourceUrl, backgroundUrl);
    }

    /** 지금 들고 있는 결과 파일 주소 */
    public Set<String> imageUrls() {
        Set<String> urls = new LinkedHashSet<>();
        if (dense != null && dense.getUrl() != null) {
            urls.add(dense.getUrl());
        }
        if (standard != null && standard.getUrl() != null) {
            urls.add(standard.getUrl());
        }
        return urls;
    }
}
