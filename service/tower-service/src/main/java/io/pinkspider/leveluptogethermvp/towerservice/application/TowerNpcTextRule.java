package io.pinkspider.leveluptogethermvp.towerservice.application;

/**
 * 탑 문장 길이 규칙.
 *
 * <p>LUT-751: NPC 대사 한 문장은 언어와 상관없이 <b>글자 수 160</b> 이내({@link #fitsNpcLine}) — 유니코드 문자 단위(DB
 * VARCHAR 와 같은 단위, 이모지 하나 = 1). 대사 카드는 줄바꿈해 전체를 보여 주고, 머리 위 말풍선은 2줄을 넘으면 말줄임표로 자른다.
 *
 * <p>LUT-693·734: 층 첫 방문 안내 문장은 말풍선 2줄 이내 — 언어마다 글자 폭이 달라 표시 폭으로 잰다({@link #fits}): 한글·한자·가나·전각
 * 문자·이모지 = 2, 그 외(라틴·아랍 문자·숫자·공백) = 1. 상한 80 = 한글 40자 = 영문 80자. (NPC 대사도 LUT-751 전까지 이 규칙이었다)
 */
public final class TowerNpcTextRule {

    public static final int MAX_LINE_WIDTH = 80;

    /** LUT-751: NPC 대사 한 문장의 글자 수 상한 */
    public static final int MAX_NPC_LINE_CHARS = 160;

    private TowerNpcTextRule() {}

    public static boolean fits(String text) {
        return text == null || displayWidth(text) <= MAX_LINE_WIDTH;
    }

    /** LUT-751: NPC 대사 — 글자(유니코드 문자) 160 이내 */
    public static boolean fitsNpcLine(String text) {
        return text == null || text.codePointCount(0, text.length()) <= MAX_NPC_LINE_CHARS;
    }

    public static int displayWidth(String text) {
        int width = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            width += isWide(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return width;
    }

    static boolean isWide(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F) // 한글 자모
                || (cp >= 0x2E80 && cp <= 0x303F) // CJK 부수·기호·구두점
                || (cp >= 0x3040 && cp <= 0x33FF) // 히라가나·가타카나·호환 자모
                || (cp >= 0x3400 && cp <= 0x4DBF) // CJK 확장 A
                || (cp >= 0x4E00 && cp <= 0x9FFF) // CJK 통합 한자
                || (cp >= 0xAC00 && cp <= 0xD7A3) // 한글 음절
                || (cp >= 0xF900 && cp <= 0xFAFF) // CJK 호환 한자
                || (cp >= 0xFF00 && cp <= 0xFF60) // 전각 문자
                || (cp >= 0xFFE0 && cp <= 0xFFE6)
                || cp >= 0x1F300; // 이모지·보충 한자
    }
}
