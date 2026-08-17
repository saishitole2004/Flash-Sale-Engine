package com.flashsale.flash_sale_engine.service;

import com.flashsale.flash_sale_engine.config.KafkaTopicConfig;
import com.flashsale.flash_sale_engine.dto.OrderEvent;
import com.flashsale.flash_sale_engine.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    public final OrderService orderService;

    @KafkaListener(topics = KafkaTopicConfig.FLASH_SALE_TOPIC, groupId = "flashsale-group")
    public void consumeOrder(OrderEvent req){
        log.info("[Kafka Consumer] Pulled order for User [{}] on Product [{}]",
                req.getUserId(), req.getProductId());
        try{
            Order order = orderService.placeOrder(req);
            log.info("[Kafka Consumer] order succesfully persisted to mysql, orderId is [{}]",order.getId());
        }
        catch (Exception e){
            log.error("[Kafka Consumer] Could not persist order for User [{}]: {}",
            req.getUserId(), e.getMessage());
        }

    }
}
