package com.proyecto.servicios.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    void redisConnectionFactory_configuraHostYPuerto() {
        RedisConfig redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "host", "127.0.0.1");
        ReflectionTestUtils.setField(redisConfig, "port", 6379);

        RedisConnectionFactory factory = redisConfig.redisConnectionFactory();

        assertNotNull(factory);
        assertTrue(factory instanceof LettuceConnectionFactory);
        LettuceConnectionFactory lettuce = (LettuceConnectionFactory) factory;
        assertEquals("127.0.0.1", lettuce.getHostName());
        assertEquals(6379, lettuce.getPort());
    }

    @Test
    void redisTemplate_inicializaConSerializadoresString() {
        RedisConfig redisConfig = new RedisConfig();
        RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, String> template = redisConfig.redisTemplate(mockFactory);

        assertNotNull(template);
        assertSame(mockFactory, template.getConnectionFactory());
        assertNotNull(template.getKeySerializer());
        assertNotNull(template.getValueSerializer());
    }
}

