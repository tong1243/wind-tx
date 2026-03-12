package com.wut.screenapptx;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync(proxyTargetClass=true)
@EnableScheduling
@EnableKafka
@MapperScan("com.wut.screendbtx.Mapper")
@ComponentScan(basePackages = {
        "com.wut.screenapptx",
        "com.wut.screencommontx",
        "com.wut.screendbtx",
        "com.wut.screenmsgtx",
})
public class ScreenAppTxApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScreenAppTxApplication.class, args);
    }

}
