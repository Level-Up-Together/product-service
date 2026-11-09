package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** LUT-760: 어드민 시드 글 — 운영자가 쓴 글, 작성자 표시는 NPC 이름. 글이 없을 때 빈 트리를 막는다 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcEntrySeedRequest(@NotBlank @Size(max = 500) String body) {}
