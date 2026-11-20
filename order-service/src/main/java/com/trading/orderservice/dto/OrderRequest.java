package com.trading.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    private String userId;
    @NotBlank(message = "Stock Symbol is Required")
    private String symbol;

    @NotBlank(message = "Order Type is Requied")
    private String type;

    @Min(value = 1,message = "Quantity must be at least 1")
    private int quantity;

}
