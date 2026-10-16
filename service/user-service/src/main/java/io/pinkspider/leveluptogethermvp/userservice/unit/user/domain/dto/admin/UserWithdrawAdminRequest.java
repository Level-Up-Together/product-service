package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** LUT-584: 어드민 즉시 탈퇴 요청 (adminId 는 로그용, reason 은 선택) */
@JsonNaming(SnakeCaseStrategy.class)
public record UserWithdrawAdminRequest(Long adminId, String reason) {}
