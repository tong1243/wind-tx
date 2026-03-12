package com.wut.screenmsgtx.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class MsgThreadPoolConfig {
    @Bean("msgCollectTaskAsyncPool")
    public Executor msgCollectTaskAsyncPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(10);
        executor.setKeepAliveSeconds(2000);
        executor.setThreadNamePrefix("MESSAGE MODULE COLLECT EXECUTOR-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean("msgTransmitTaskAsyncPool")
    public Executor msgTransmitTaskAsyncPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(200);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(10);
        executor.setKeepAliveSeconds(200);
        executor.setThreadNamePrefix("MESSAGE MODULE TRANSMIT EXECUTOR-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean("msgExpireTaskAsyncPool")
    public Executor msgExpireTaskAsyncPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(10);
        executor.setKeepAliveSeconds(200);
        executor.setThreadNamePrefix("MESSAGE MODULE EXPIRE EXECUTOR-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

}
