package com.trading.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;

@FeignClient(name="user-service",url="${user.service.url}")
public interface UserServiceClient {

    @PostMapping("/api/v1/users/{userId}/funds/deduct")
    Map<String,Object> deductFunds(@PathVariable String userId,
                                   @RequestParam BigDecimal amount);

    @PostMapping("/api/v1/users/{userId}/funds/credict")
    Map<String,Object> credictFunds(@PathVariable String userId,
                                   @RequestParam BigDecimal amount);

}
