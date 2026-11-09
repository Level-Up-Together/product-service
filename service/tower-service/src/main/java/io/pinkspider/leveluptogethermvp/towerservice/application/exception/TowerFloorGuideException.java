package io.pinkspider.leveluptogethermvp.towerservice.application.exception;

import org.springframework.http.HttpStatus;

/** LUT-734: 첫 방문 안내 저장 오류(어드민 입력, 400). 코드 1506xx. 층이 없으면 150101. */
public class TowerFloorGuideException extends TowerException {

    public static final String NPC_INVALID = "150601";
    public static final String LINE_TOO_LONG = "150602";
    public static final String LINES_REQUIRED = "150603";

    private TowerFloorGuideException(String code, String messageKey) {
        super(code, messageKey, HttpStatus.BAD_REQUEST);
    }

    /** 켜려면 안내 NPC 가 필요하고, 그 층의 NPC 여야 한다 */
    public static TowerFloorGuideException npcInvalid() {
        return new TowerFloorGuideException(NPC_INVALID, "error.tower.guide.npc_invalid");
    }

    /** 안내 문장 한 문장이 2줄(한글 약 40자)을 넘는다 */
    public static TowerFloorGuideException lineTooLong() {
        return new TowerFloorGuideException(LINE_TOO_LONG, "error.tower.guide.line_too_long");
    }

    /** 켜려면 문장이 1개 이상 있어야 한다 */
    public static TowerFloorGuideException linesRequired() {
        return new TowerFloorGuideException(LINES_REQUIRED, "error.tower.guide.lines_required");
    }
}
