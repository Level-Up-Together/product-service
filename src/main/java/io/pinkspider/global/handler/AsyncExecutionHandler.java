package io.pinkspider.global.handler;

import static io.pinkspider.global.util.ObjectMapperUtils.toJson;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;


/*
 * AsyncExecutionHandler
 * 비동기 오류는 RejectedExecutionHandler를 구현한다.
 */
@Slf4j
public class AsyncExecutionHandler implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        log.error("Async Exception is occurred!");
        log.error("runnable = {}", toJson(runnable));
        log.error("executor = {}", toJson(executor));
    }
}

