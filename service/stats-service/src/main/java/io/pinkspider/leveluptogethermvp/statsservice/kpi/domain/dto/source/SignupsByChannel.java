package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

/** LUT-602: KST 월('YYYY-MM') × 가입 채널 × 가입자 수 (CAC 분모). 채널 'unknown' = LUT-599 배포 전 가입자 */
public record SignupsByChannel(String month, String channel, int signups) {}
