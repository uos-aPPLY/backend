package com.apply.diarypic.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 사진 업로드 전용 비동기 작업 스레드 풀
     */
    @Bean(name = "photoUploadTaskExecutor")
    public Executor photoUploadTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int corePoolSize = 2;
        int maxPoolSize = 4;
        int queueCapacity = 50;

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("PhotoUploadAsync-");
        executor.initialize();
        log.info("Custom ThreadPoolTaskExecutor 'photoUploadTaskExecutor' initialized with corePoolSize: {}, maxPoolSize: {}, queueCapacity: {}", corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "diaryAiTaskExecutor")
    public Executor diaryAiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); // AI 작업은 CPU를 사용할 수 있으므로 코어 수에 맞추거나 약간 적게
        executor.setMaxPoolSize(5);  // 너무 많은 동시 AI 요청 방지
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("DiaryAI-");
        executor.initialize();
        log.info("Custom ThreadPoolTaskExecutor 'diaryAiTaskExecutor' initialized.");
        return executor;
    }
}