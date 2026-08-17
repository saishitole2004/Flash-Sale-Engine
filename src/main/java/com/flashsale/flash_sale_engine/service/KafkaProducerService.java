package com.flashsale.flash_sale_engine.service;

import com.flashsale.flash_sale_engine.config.KafkaTopicConfig;
import com.flashsale.flash_sale_engine.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendOrderEvent(OrderEvent req){
        kafkaTemplate.send(KafkaTopicConfig.FLASH_SALE_TOPIC, String.valueOf(req.getProductId()),req);
        log.info("[Kafka Producer] Published Order for User [{}] on Product [{}]",
                req.getUserId(), req.getProductId());
    }
}
