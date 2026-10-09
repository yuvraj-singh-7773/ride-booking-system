package com.example.Uber.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RedisConfigTest {

    @Test
    void testRedisConnectionFactoryCreation() {
        RedisConfig config = new RedisConfig();
        RedisConnectionFactory factory = config.redisConnectionFactory();
        assertNotNull(factory);
    }
}
