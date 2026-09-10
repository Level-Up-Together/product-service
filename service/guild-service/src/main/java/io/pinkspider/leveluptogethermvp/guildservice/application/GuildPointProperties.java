package io.pinkspider.leveluptogethermvp.guildservice.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LUT-483: 길드 활동 포인트 사다리 설정.
 *
 * <p>유저 1명의 하루 점수 = floor(min(하루 길드미션 EXP, dailyCapExp) / unitExp).
 * 상한이 카테고리 편향(하루에 쌓을 수 있는 EXP 총량 차이)을 막는 핵심 장치다.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "guild.point")
public class GuildPointProperties {

    /** 점수 1점당 EXP 단위 */
    private int unitExp = 10;

    /** 유저당 하루 점수 상한의 EXP 기준 (60 = 하루 최대 6점) */
    private int dailyCapExp = 60;
}
