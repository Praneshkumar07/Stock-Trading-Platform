package com.trading.orderservice.service;

import com.trading.orderservice.client.MarketDataClient;
import com.trading.orderservice.client.UserServiceClient;
import com.trading.orderservice.dto.OrderRequest;
import com.trading.orderservice.entity.Order;
import com.trading.orderservice.entity.OrderStatus;
import com.trading.orderservice.entity.OrderType;
import com.trading.orderservice.repository.OrderRepository;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final MarketDataClient marketDataClient;
    private final UserServiceClient userServiceClient;

    private static final String ORDER_EXECUTED_TOPIC="Order.Executed";
    private static final String ORDER_FAILED_TOPIC="Order.Failed";
    private final KafkaTemplate<String,Object> kafkaTemplate;


    /**
     * Flow
     * 1.Get the Current Price from Market
     * 2.Calculate Total amount
     * 3.Execute
     * 4.Publish Event for Kafka
     * @param request
     * @return
     */
    public Order placeOrder(OrderRequest request){
        log.info("Placing {} order :{} x {} for User :{}",request.getType(),
                request.getQuantity(),request.getSymbol(),request.getUserId());

        Map<String,Object> priceData=marketDataClient.getStockPrice(request.getSymbol());
        BigDecimal currentPrice=new BigDecimal(priceData.get("price").toString());
        BigDecimal totalAmount=currentPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        //create Order

        Order order=new Order();
        order.setUserId(request.getUserId());
        order.setSymbol(request.getSymbol());
        order.setQuantity(request.getQuantity());
        order.setPrice(currentPrice);
        order.setType(OrderType.valueOf(request.getType()));
        order.setTotalAmount(totalAmount);
        order.setOrderStatus(OrderStatus.PENDING);

        Order savedOrder=orderRepository.save(order);
        log.info("Order Created:{}",savedOrder.getOrderId());

        try{

            if(OrderType.valueOf(request.getType()) == OrderType.BUY){
                userServiceClient.deductFunds(request.getUserId(),totalAmount);
            }else{
                userServiceClient.credictFunds(request.getUserId(),totalAmount);
            }
            savedOrder.setOrderStatus(OrderStatus.EXCECUTED);
            savedOrder.setExecutedAt(LocalDateTime.now());

            orderRepository.save(savedOrder);

            log.info("Order Executed:{} {} x {} at {}", request.getType(),
                    request.getQuantity(),request.getSymbol(),currentPrice);
            publishOrderEvent(ORDER_EXECUTED_TOPIC,savedOrder,null);

        }
        catch (Exception e){
            savedOrder.setOrderStatus(OrderStatus.FAILED);
            savedOrder.setFailureReason(e.getMessage());
            orderRepository.save(savedOrder);
            log.error("Order Failed {} reason {}",savedOrder.getOrderId(),e.getMessage());

            publishOrderEvent(ORDER_FAILED_TOPIC,savedOrder, e.getMessage());
        }
        return savedOrder;

    }

    public Order getOrder(String orderId){
        return orderRepository.findById(orderId).
                orElseThrow(()->new RuntimeException("Order Not Found"+orderId));

    }
    public List<Order> getMyOrders(String userId){
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private void publishOrderEvent(String topic, Order order , String reason){
        Map<String,Object> orderEvent=new HashMap<>();
        orderEvent.put("orderId",order.getOrderId());
        orderEvent.put("userId",order.getUserId());
        orderEvent.put("symbol",order.getSymbol());
        orderEvent.put("type",order.getType().name());
        orderEvent.put("quantity",order.getQuantity());
        orderEvent.put("price",order.getPrice());
        orderEvent.put("totalAmount",order.getTotalAmount());
        orderEvent.put("status",order.getOrderStatus());
        if(reason!=null)orderEvent.put("reason ",reason);
        kafkaTemplate.send(topic,order.getOrderId(),orderEvent);
        log.info("Event Published :{} for each order :{}",topic,order.getOrderId());


    }

}
