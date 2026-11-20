
package com.trading.marketdataservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template =
                new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);

        // Store Redis keys as strings
        template.setKeySerializer(
                new StringRedisSerializer());

        // Store values as JSON
        template.setValueSerializer(
                RedisSerializer.json());

        // Apply serializers to hash operations too
        template.setHashKeySerializer(
                new StringRedisSerializer());

        template.setHashValueSerializer(
                RedisSerializer.json());

        return template;
    }
}
