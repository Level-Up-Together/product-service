package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** LUT-727: 픽셀 배경 한 장 — 주소·크기(px)·버전(내용 해시). 컬럼 이름은 {@code AttributeOverride} 로 종류별 접두사를 붙인다 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TowerPixelImage {

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "version", length = 32)
    private String version;
}
