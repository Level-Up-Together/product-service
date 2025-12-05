package io.pinkspider.global.config;

import io.pinkspider.global.handler.AsyncExecutionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;


// 일반적인 (log, access등) 비동기 서비스의 경우에는 generalExcutor를 사용한다.
// 필요하다면 용도에 맞춰서 async task executor를 커스텀 설정해서 사용한다..
@Configuration
@EnableAsync
public class AsyncConfig {

    // yml에 설정 할수 있지만 코드 레벨에서 직접 관리한다. 필요에 따라서 이 값은 변경될 수 있다.
    private static final int POOL_SIZE = 8;
    private static final int MAX_POOL_SIZE = 16;
    private static final int QUEUE_CAPACITY = 100;

    public static final String GENERAL_EXECUTOR = "generalExecutor";

    @Bean(name = GENERAL_EXECUTOR)
    public TaskExecutor taskExecutor() {
        return generateThreadPoolTaskExecutor();
    }

    // 지정된 풀 사이즈로 TaskExecutor을 설정한다.
    private TaskExecutor generateThreadPoolTaskExecutor() {
        return generateThreadPoolTaskExecutor(POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);
    }

    // 지정된 풀 사이즈가 아닌 커스텀 폴 사이즈로 TaskExecutor을 설정한다.
    private TaskExecutor generateThreadPoolTaskExecutor(int poolSize, int maxPoolSize, int queueCapacity) {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(poolSize);
        taskExecutor.setMaxPoolSize(maxPoolSize);
        taskExecutor.setQueueCapacity(queueCapacity);
        taskExecutor.setRejectedExecutionHandler(new AsyncExecutionHandler());
        taskExecutor.afterPropertiesSet();
        taskExecutor.initialize();
        return taskExecutor;
    }
}
