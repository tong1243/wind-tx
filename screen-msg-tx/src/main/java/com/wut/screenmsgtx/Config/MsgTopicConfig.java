package com.wut.screenmsgtx.Config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import static com.wut.screencommontx.Static.MsgModuleStatic.*;

@Configuration
public class MsgTopicConfig {

    @Bean("topicFiber")
    public NewTopic topicFiber() {
        return TopicBuilder.name(TOPIC_NAME_FIBER).partitions(TOPIC_PARTITION).replicas(TOPIC_REPLICA).build();
    }


    @Bean("topicDirect")
    public NewTopic topicDirect() {
        return TopicBuilder.name(TOPIC_NAME_DIRECT).partitions(TOPIC_PARTITION).replicas(TOPIC_REPLICA).build();
    }

}
