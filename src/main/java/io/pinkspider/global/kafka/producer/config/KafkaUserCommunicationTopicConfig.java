package io.pinkspider.global.kafka.producer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaUserCommunicationTopicConfig {

    @Value("${spring.kafka.user-communication.topic}")
    String userCommunicationTopic;

    @Bean(name = "userCommunicationTopic")
    public NewTopic alimTalkTopic() {
        return new NewTopic(userCommunicationTopic, 3, (short) 1);
    }
}
