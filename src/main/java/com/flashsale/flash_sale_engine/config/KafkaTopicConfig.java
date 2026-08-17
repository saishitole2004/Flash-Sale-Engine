package com.flashsale.flash_sale_engine.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

public class KafkaTopicConfig {

    public static final String FLASH_SALE_TOPIC = "flashsale-orders";

    @Bean
    public NewTopic flashSaleOrders(){
        return TopicBuilder.name(FLASH_SALE_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
