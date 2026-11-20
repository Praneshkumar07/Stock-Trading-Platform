package com.trading.marketdataservice.service;

import com.trading.marketdataservice.dto.StockPrice;
import com.trading.marketdataservice.entity.Stock;
import com.trading.marketdataservice.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
@Order(2)
public class MarketDataService implements CommandLineRunner {

    private final StockRepository stockRepository;
    private final Random random=new Random();
    private final RedisTemplate<String,Object> redisTemplate;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final KafkaTemplate<String,Object> kafkaTemplate;


    private static final String PRICE_KEY_PREFIX="stock:price:";
    private static final String PRICE_UPDATED_TOPIC ="stock.price.updated";
    private final Map<String, StockPrice> stockPrices=new ConcurrentHashMap<>();

    @Override
    public void run(String...args){
        log.info("Loadding stock data.....");

        List<Stock> stocks=stockRepository.findByActiveTrue();
        if(stocks.isEmpty()){
            log.warn("No active Stocks Found");
            return;
        }
        stocks.forEach(stock -> {
            StockPrice stockPrice=new StockPrice();
            stockPrice.setSymbol(stock.getSymbol());
            stockPrice.setCompanyName(stock.getCompanyName());
            stockPrice.setPrice(stock.getInitialPrice());
            stockPrice.setOpen(stock.getInitialPrice());
            stockPrice.setHigh(stock.getInitialPrice());
            stockPrice.setLow(stock.getInitialPrice());
            stockPrice.setChange(BigDecimal.ZERO);
            stockPrice.setChangePercent(BigDecimal.ZERO);
            stockPrice.setVolume(0L);
            stockPrice.setTimestamp(LocalDateTime.now());
            stockPrices.put(stockPrice.getSymbol(),stockPrice);

                log.info("Stock {} loaded at {}",stock.getSymbol(),stock.getInitialPrice());

            });
        log.info("Initialized {} stocks",stockPrices.size());

    }

    @Scheduled(fixedRateString = "${market.price-update-interval}")
    public void updatePrices(){
        stockPrices.forEach((symbol,currentPrice)->{
            if(stockPrices.isEmpty()){
                log.info("No Stocks Found");
                return;
            }
            BigDecimal oldPrice=currentPrice.getPrice();
            double priceMovement=(random.nextDouble()-0.5)*0.5;
            BigDecimal priceChange=oldPrice.multiply(BigDecimal.valueOf(priceMovement/100))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal newPrice=oldPrice.add(priceChange).setScale(2,RoundingMode.HALF_UP);


            if(newPrice.compareTo(BigDecimal.valueOf(1))<0){
                newPrice=BigDecimal.valueOf(1);
            }
            BigDecimal changeAmount=newPrice.subtract(currentPrice.getOpen()).setScale(2,RoundingMode.HALF_UP);

            BigDecimal changePercent=changeAmount.
                    divide(currentPrice.getOpen(),4,RoundingMode.HALF_UP).
                    multiply(BigDecimal.valueOf(100)).setScale(2,RoundingMode.HALF_UP);

            BigDecimal newHigh=newPrice.compareTo(currentPrice.getHigh())>0?
                    newPrice:currentPrice.getHigh();

            BigDecimal newLow=newPrice.compareTo(currentPrice.getLow())<0?newPrice:currentPrice.getLow();

            currentPrice.setPrice(newPrice);
            currentPrice.setChange(changeAmount);
            currentPrice.setChangePercent(changePercent);
            currentPrice.setHigh(newHigh);
            currentPrice.setLow(newLow);
            currentPrice.setTimestamp(LocalDateTime.now());


//          1.Cache

            redisTemplate.opsForValue().set(PRICE_KEY_PREFIX+symbol,currentPrice);

//          2.BroadCast via Websocket -> all Clients
            simpMessagingTemplate.convertAndSend("/topic/prices"+symbol,currentPrice);

//          3.Publish to kalka
            Map<String,Object> priceUpdatedEvent=new HashMap<>();
            priceUpdatedEvent.put("symbol",symbol);
            priceUpdatedEvent.put("price",newPrice);
            priceUpdatedEvent.put("timeStamp",LocalDateTime.now());

            kafkaTemplate.send(PRICE_UPDATED_TOPIC,symbol,priceUpdatedEvent);



        });
        log.info("Prices updated for {} stocks",stockPrices.size());

    }
    public StockPrice getPrice(String symbol){
        Object cached=redisTemplate.opsForValue().get(PRICE_KEY_PREFIX+symbol);
        if(cached!=null) return (StockPrice) cached;
        StockPrice price=stockPrices.get(symbol.toUpperCase());
        if(price==null) throw new RuntimeException("stock Not Found "+symbol);
        return price;
    }

    public List<StockPrice> getAllPrices(){
        return stockPrices.values().stream().toList();
    }

    public List<Stock> getAllStocks(){
        return stockRepository.findByActiveTrue();
    }
}
