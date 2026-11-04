package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerChatLog;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerChatLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** LUT-633: 운영 로그 쓰기 — 전송 흐름과 분리해 거절(예외)된 메시지도 남긴다. 실패해도 채팅은 막지 않는다. */
@Component
@Slf4j
@RequiredArgsConstructor
public class TowerChatLogWriter {
    private final TowerChatLogRepository repository;

    @Transactional(transactionManager = "towerTransactionManager")
    public void write(TowerChatLog log) {
        try {
            repository.save(log);
        } catch (RuntimeException e) {
            TowerChatLogWriter.log.warn("[tower-chat] 로그 저장 실패: {}", e.getMessage());
        }
    }
}
