package io.pinkspider.global.kafka.topic;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaAlimTalkTopicConfig {

    @Value("${spring.kafka.alim-talk.topic}")
    String alimTalkTopic;

    @Bean(name = "alimTalkTopic")
    public NewTopic alimTalkTopic() {
        return new NewTopic(alimTalkTopic, 3, (short) 1);
    }
}
