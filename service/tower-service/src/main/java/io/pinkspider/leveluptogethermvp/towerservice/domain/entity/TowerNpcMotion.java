package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcMotionPlayMode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * LUT-864: NPC 움직이는 그림(스프라이트 시트 한 장 + 프레임 정보). 시트는 층 이미지 업로드 {@code kind=NPC_MOTION} 이 발급한 주소, 프레임은
 * 재생 순서대로 {@code [{x,y,w,h,duration_ms}]} JSON. 발 위치·그림 맨 위는 칸 안 px(모든 장 공통), 키는 화면 그림 키(아바타 좌표
 * px). 없으면 컬럼이 모두 null 이고 앱은 원형 썸네일로 그린다.
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TowerNpcMotion {

    @Column(name = "motion_sheet_url", length = 500)
    private String sheetUrl;

    /** 재생 순서대로 {@code [{"x":0,"y":0,"w":256,"h":256,"duration_ms":128}, …]} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "motion_frames")
    private String frames;

    @Column(name = "motion_foot_x")
    private Integer footX;

    @Column(name = "motion_foot_y")
    private Integer footY;

    @Column(name = "motion_content_top")
    private Integer contentTop;

    @Column(name = "motion_height")
    private Integer height;

    @Column(name = "motion_flip")
    private Boolean flip;

    @Enumerated(EnumType.STRING)
    @Column(name = "motion_play_mode", length = 12)
    private TowerNpcMotionPlayMode playMode;

    @Column(name = "motion_interval_seconds")
    private Integer intervalSeconds;
}
