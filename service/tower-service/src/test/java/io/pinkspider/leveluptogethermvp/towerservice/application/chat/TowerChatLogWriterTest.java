package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerChatLog;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerChatLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TowerChatLogWriterTest {
    @Mock private TowerChatLogRepository repository;
    @InjectMocks private TowerChatLogWriter writer;

    @Test
    void writesAndSwallowsFailure() {
        TowerChatLog log =
                TowerChatLog.builder()
                        .instanceId(1L)
                        .floorNumber(1)
                        .originalContent("x")
                        .result("SENT")
                        .build();
        writer.write(log);
        verify(repository).save(log);
        when(repository.save(any())).thenThrow(new RuntimeException("db"));
        writer.write(log); // 예외 없이 끝난다
    }
}
