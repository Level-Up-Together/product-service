package io.pinkspider.leveluptogethermvp.towerservice.application.pixel;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * LUT-727: 픽셀아트 변환 전용 실행기 — 한 장에 CPU 수 초·메모리 수십 MB 라 한 번에 하나만 돌린다(업로드가 드물다). 대기열이 차면(20) 요청 스레드에서
 * 돌리지 않고 버린다 — 행은 대기로 남아 복구 스케줄러가 다시 보낸다.
 */
@Configuration
public class TowerPixelArtConfig {

    public static final String EXECUTOR = "towerPixelArtExecutor";

    @Bean(name = EXECUTOR)
    public Executor towerPixelArtExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("tower-pixel-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    @Bean
    public TowerPixelArtConverter towerPixelArtConverter() {
        return new TowerPixelArtConverter();
    }
}
