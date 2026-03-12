package com.wut.screenmsgtx.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static com.wut.screencommontx.Static.MsgModuleStatic.TASK_AWAIT;
import static com.wut.screencommontx.Static.MsgModuleStatic.TASK_POOL_SIZE;

@Configuration
public class MsgTaskSchedulerConfig {
    @Bean("collectDataTaskScheduler")
    public ThreadPoolTaskScheduler collectThreadPoolTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(TASK_POOL_SIZE);
        scheduler.setThreadNamePrefix("COLLECT TASK SCHEDULER-");
        scheduler.setAwaitTerminationSeconds(TASK_AWAIT);
        scheduler.initialize();
        return scheduler;
    }

    @Bean("transmitDataTaskScheduler")
    public ThreadPoolTaskScheduler transmitThreadPoolTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(TASK_POOL_SIZE);
        scheduler.setThreadNamePrefix("TRANSMIT TASK SCHEDULER-");
        scheduler.setAwaitTerminationSeconds(TASK_AWAIT);
        scheduler.initialize();
        return scheduler;
    }

}
