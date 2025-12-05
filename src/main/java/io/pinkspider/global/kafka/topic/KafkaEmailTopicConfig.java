package io.pinkspider.global.kafka.topic;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaEmailTopicConfig {

    @Value("${spring.kafka.email.topic}")
    String emailTopic;

    @Bean(name = "emailTopic")
    public NewTopic emailTopic() {
        return new NewTopic(emailTopic, 3, (short) 1);
    }
}
