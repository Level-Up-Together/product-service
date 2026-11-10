package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import io.pinkspider.leveluptogethermvp.profanity.application.ProfanityValidationService;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanitySeverity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LUT-633: 채팅 금칙어 처리. 어드민 비속어 목록(분류·심각도·사용 여부, 캐시 {@code profanityWords} — 어드민 변경 시 evict)을 쓴다.
 *
 * <ul>
 *   <li>비교는 공백·특수문자를 지운 소문자 문장으로 한다(기본 우회 방지: "ㅅ ㅂ", "s.h.i.t").
 *   <li>LOW·MEDIUM — 해당 단어만 {@code **} 로 가리고 전송. MEDIUM 반복은 {@link TowerChatGuard} 가 제한.
 *   <li>HIGH — 메시지 전체 거부(150305). 원문은 운영 로그에만.
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TowerChatModerationService {
    static final String MASK = "**";
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]");

    private final ProfanityValidationService profanityValidationService;

    public record ModerationResult(
            String content,
            boolean masked,
            boolean rejected,
            ProfanitySeverity maxSeverity,
            List<String> matchedWords) {
        public static ModerationResult clean(String content) {
            return new ModerationResult(content, false, false, null, List.of());
        }
    }

    public ModerationResult moderate(String content) {
        if (content == null || content.isBlank()) {
            return ModerationResult.clean(content);
        }
        Map<String, String> words =
                profanityValidationService.getActiveProfanityWordsWithSeverity();
        if (words == null || words.isEmpty()) {
            return ModerationResult.clean(content);
        }
        String normalized = normalize(content);
        List<String> matched = new ArrayList<>();
        ProfanitySeverity max = null;
        String processed = content;
        for (Map.Entry<String, String> entry : words.entrySet()) {
            String word = entry.getKey();
            String normalizedWord = normalize(word);
            if (normalizedWord.isEmpty() || !normalized.contains(normalizedWord)) {
                continue;
            }
            ProfanitySeverity severity = parseSeverity(entry.getValue());
            matched.add(word);
            if (max == null || severity.ordinal() > max.ordinal()) {
                max = severity;
            }
            if (severity != ProfanitySeverity.HIGH) {
                processed = mask(processed, normalizedWord);
            }
        }
        if (matched.isEmpty()) {
            return ModerationResult.clean(content);
        }
        if (max == ProfanitySeverity.HIGH) {
            return new ModerationResult(null, false, true, max, matched);
        }
        return new ModerationResult(processed, !processed.equals(content), false, max, matched);
    }

    /** 소문자 + 글자·숫자만 */
    static String normalize(String text) {
        return NON_ALNUM.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /** 원문에서 글자 사이 공백·특수문자를 허용하며 단어를 찾아 {@code **} 로 바꾼다 */
    static String mask(String content, String normalizedWord) {
        StringBuilder regex = new StringBuilder();
        int[] codePoints = normalizedWord.codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            regex.append(Pattern.quote(new String(Character.toChars(codePoints[i]))));
            if (i < codePoints.length - 1) {
                regex.append("[^\\p{L}\\p{N}]*");
            }
        }
        Pattern pattern =
                Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(content);
        return matcher.replaceAll(Matcher.quoteReplacement(MASK));
    }

    private static ProfanitySeverity parseSeverity(String raw) {
        try {
            return raw == null ? ProfanitySeverity.MEDIUM : ProfanitySeverity.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return ProfanitySeverity.MEDIUM;
        }
    }
}
