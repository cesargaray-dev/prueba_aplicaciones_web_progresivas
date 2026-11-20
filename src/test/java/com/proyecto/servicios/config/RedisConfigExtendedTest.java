package com.proyecto.servicios.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class RedisConfigExtendedTest {

    @Test
    void redisTemplate_cuandoRedisDeshabilitado_retornaStubFailFastSinBloqueo() {
        RedisConfig config = new RedisConfig();
        ReflectionTestUtils.setField(config, "redisEnabled", false);
        ReflectionTestUtils.setField(config, "host", "localhost");
        ReflectionTestUtils.setField(config, "port", 6379);

        var factory = config.redisConnectionFactory();
        assertNotNull(factory);

        RedisTemplate<String, String> template = config.redisTemplate(factory);
        assertNotNull(template);
        assertThrows(RedisConnectionFailureException.class, () -> template.opsForValue().get("clave"));
    }

    @Test
    void redisHealthIndicator_retornaUpCuandoRedisDeshabilitado() {
        RedisConfig config = new RedisConfig();
        HealthIndicator indicator = config.redisHealthIndicator();
        Health health = indicator.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals(false, health.getDetails().get("enabled"));
    }
}
