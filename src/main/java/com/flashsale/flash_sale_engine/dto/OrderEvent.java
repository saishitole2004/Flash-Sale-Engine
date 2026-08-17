package com.flashsale.flash_sale_engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent implements Serializable {
    private Long userId;
    private Long productId;
    private Integer quantity;
    private LocalDateTime timestamp;
}