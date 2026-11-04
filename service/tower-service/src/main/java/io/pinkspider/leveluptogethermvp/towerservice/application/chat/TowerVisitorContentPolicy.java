package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import java.util.Optional;
import java.util.regex.Pattern;

/** LUT-633/705: 방문객 채팅 내용 제한 — URL·전화번호·메신저 아이디는 보내지 못한다(스팸·개인정보 유도 방지) */
public final class TowerVisitorContentPolicy {
    public static final String REASON_URL = "url";
    public static final String REASON_PHONE = "phone";
    public static final String REASON_MESSENGER = "messenger";

    private static final Pattern URL =
            Pattern.compile(
                    "(?i)(https?://|www\\.|[a-z0-9-]+\\.(com|net|org|kr|co|io|me|gg|xyz|link|app|site|shop)(/|\\b))");
    private static final Pattern PHONE = Pattern.compile("\\d[\\d\\-\\s.]{6,}\\d");
    private static final Pattern MESSENGER =
            Pattern.compile(
                    "(?i)(카톡|카카오톡|카카오\\s*아이디|kakao|telegram|텔레그램|텔레\\b|discord|디스코드|instagram|insta\\b|인스타|line\\s*id|라인\\s*아이디|wechat|위챗|@[a-z0-9_.]{3,})");

    private TowerVisitorContentPolicy() {}

    /** 위반 사유 (없으면 empty) */
    public static Optional<String> violation(String content) {
        if (content == null) {
            return Optional.empty();
        }
        if (URL.matcher(content).find()) {
            return Optional.of(REASON_URL);
        }
        if (PHONE.matcher(content).find()) {
            return Optional.of(REASON_PHONE);
        }
        if (MESSENGER.matcher(content).find()) {
            return Optional.of(REASON_MESSENGER);
        }
        return Optional.empty();
    }
}
