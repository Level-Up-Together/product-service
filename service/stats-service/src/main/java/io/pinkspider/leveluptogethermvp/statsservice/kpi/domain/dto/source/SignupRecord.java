package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

import java.time.LocalDateTime;

/**
 * LUT-779: 가입 1건 — 첫날 행동 분류 원천.
 *
 * @param createdAt 가입 시각(UTC)
 * @param signupDeviceType 가입 기기 {@code ios·ipad·android·web}(LUT-599), 그 전 가입자는 null
 */
public record SignupRecord(String userId, LocalDateTime createdAt, String signupDeviceType) {}
