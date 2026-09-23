package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 길드 초대 링크 코드 생성기 (LUT-519).
 *
 * <p>추측 불가한 랜덤 코드를 만든다. 혼동하기 쉬운 문자(0/O, 1/I/L)를 제외한 32자 알파벳에서 10자를 뽑아
 * 32^10 ≈ 1.1e15 의 공간을 갖는다. 유니크 제약 충돌 시 몇 차례 재시도한다.
 */
@Component
@RequiredArgsConstructor
public class GuildInviteCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // 0/O/1/I/L 제외 (31자)
    private static final int CODE_LENGTH = 10;
    private static final int MAX_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GuildRepository guildRepository;

    /** 아직 쓰이지 않은 초대 코드를 생성한다. */
    public String generateUnique() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!guildRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드 생성에 실패했습니다. 다시 시도해주세요.");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
