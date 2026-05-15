package io.pinkspider.leveluptogethermvp.userservice.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 회원탈퇴 정책 설정 (QA-115).
 * 탈퇴 후 cool-down 기간이 지나야 동일 이메일+provider 로 재가입 가능.
 * 기본값 7일. config-server / 프로파일별 yml 로 조정.
 */
@Configuration
@ConfigurationProperties(prefix = "app.user.withdrawal")
@Data
public class WithdrawalProperties {

    private int coolDownDays = 7;
}
