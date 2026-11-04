package io.pinkspider.leveluptogethermvp.towerservice.application;

/**
 * LUT-693: NPC 대사 한 문장은 대사 카드에서 2줄 이내여야 한다 — 기획 기준 "한글 약 40자". 언어마다 글자 폭이 달라 글자 수가 아니라 표시 폭으로 잰다:
 * 한글·한자·가나·전각 문자·이모지 = 2, 그 외(라틴·아랍 문자·숫자·공백) = 1. 상한 80 = 한글 40자 = 영문 80자.
 */
public final class TowerNpcTextRule {

    public static final int MAX_LINE_WIDTH = 80;

    private TowerNpcTextRule() {}

    public static boolean fits(String text) {
        return text == null || displayWidth(text) <= MAX_LINE_WIDTH;
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
