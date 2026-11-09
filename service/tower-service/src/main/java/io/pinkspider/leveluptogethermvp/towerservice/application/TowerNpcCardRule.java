package io.pinkspider.leveluptogethermvp.towerservice.application;

/**
 * LUT-733: NPC 창 링크의 앱 안 경로 규칙 — {@code /} 로 시작하는 앱 경로만. 다른 사이트로 나가는 주소({@code //host}·{@code
 * https://…})·공백·역슬래시는 거부한다(브라우저가 {@code /\host} 를 다른 호스트로 해석한다). 웹 {@code tower-npc.ts} 와 같은 규칙.
 */
public final class TowerNpcCardRule {

    public static final int PATH_MAX_LENGTH = 300;

    private TowerNpcCardRule() {}

    public static boolean isAppPath(String path) {
        if (path == null || path.isEmpty() || path.length() > PATH_MAX_LENGTH) {
            return false;
        }
        if (path.charAt(0) != '/' || path.startsWith("//") || path.contains("://")) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }
}
