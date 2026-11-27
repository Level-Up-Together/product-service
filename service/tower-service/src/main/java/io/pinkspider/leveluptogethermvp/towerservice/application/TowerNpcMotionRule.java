package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcMotionDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcMotionFrameDto;
import java.util.List;

/**
 * LUT-864: 움직이는 그림 값 검사(어기면 150416). 범위(@Min·@Max·@Size)는 요청 검증이 먼저 막고, 여기서는 값 사이의 관계를 본다 — 장이 1개
 * 이상, 칸이 시트(한 변 4096px) 안, 발·그림 맨 위가 있고 {@code 0 ≤ foot_x ≤ 칸 폭}·{@code content_top < foot_y ≤ 칸
 * 높이}(장마다 크기가 다르면 가장 큰 칸 기준). 앱(LUT-865 {@code toNpcMotion})이 같은 조건을 어기면 원형 썸네일로 돌아가므로 저장 때 막는다.
 */
final class TowerNpcMotionRule {

    private TowerNpcMotionRule() {}

    static boolean isValid(TowerNpcMotionDto motion) {
        List<TowerNpcMotionFrameDto> frames = motion.frames();
        if (frames == null || frames.isEmpty() || frames.size() > TowerNpcMotionDto.MAX_FRAMES) {
            return false;
        }
        int maxW = 0;
        int maxH = 0;
        for (TowerNpcMotionFrameDto frame : frames) {
            if (frame == null
                    || frame.x() == null
                    || frame.y() == null
                    || frame.w() == null
                    || frame.h() == null
                    || frame.x() + frame.w() > TowerNpcMotionDto.MAX_SHEET_PX
                    || frame.y() + frame.h() > TowerNpcMotionDto.MAX_SHEET_PX) {
                return false;
            }
            maxW = Math.max(maxW, frame.w());
            maxH = Math.max(maxH, frame.h());
        }
        Integer footX = motion.footX();
        Integer footY = motion.footY();
        Integer contentTop = motion.contentTop();
        return footX != null
                && footY != null
                && contentTop != null
                && footX >= 0
                && footX <= maxW
                && contentTop >= 0
                && contentTop < footY
                && footY <= maxH;
    }
}
