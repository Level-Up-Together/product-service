package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** LUT-633: 탑 채팅 제한값 — config-repository 환경별로 덮어쓸 수 있다 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tower.chat")
public class TowerChatProperties {
    /** 로그인 유저 전송 간격 */
    private long userIntervalMs = 1000;

    /** 방문객 전송 간격 (LUT-705) */
    private long visitorIntervalMs = 5000;

    /** 로그인 유저 최대 글자 수 */
    private int maxLength = 200;

    /** 방문객 최대 글자 수 (LUT-705) */
    private int visitorMaxLength = 40;

    /** 같은 내용 연속 전송 차단 창 */
    private int duplicateWindowSeconds = 60;

    /** MEDIUM 비속어를 이 창 안에서 이 횟수 쓰면 잠시 제한 */
    private int mediumRepeatCount = 3;

    private int mediumWindowSeconds = 60;

    /** 제한 시간 */
    private int muteSeconds = 300;

    /** 최근 조회 건수 */
    private int historySize = 50;

    private int historyTtlHours = 24;

    /** 리액션 프리셋 (서버가 정한다) */
    private List<String> reactionEmojis = List.of("👍", "❤️", "🔥", "👏", "😂", "💪");
}
