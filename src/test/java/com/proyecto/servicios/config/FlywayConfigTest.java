package com.proyecto.servicios.config;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class FlywayConfigTest {

    @Test
    void flyway_construyeInstanciaFlywayConConfiguracion() {
        FlywayConfig flywayConfig = new FlywayConfig();
        ReflectionTestUtils.setField(flywayConfig, "locations", new String[]{"classpath:db/migration_test_empty"});
        ReflectionTestUtils.setField(flywayConfig, "historyTable", "flyway_schema_history");
        ReflectionTestUtils.setField(flywayConfig, "schema", "PUBLIC");

        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:flywaytest_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        Flyway flyway = flywayConfig.flyway(ds);

        assertNotNull(flyway);
        assertEquals("flyway_schema_history", flyway.getConfiguration().getTable());
        assertArrayEquals(new String[]{"PUBLIC"}, flyway.getConfiguration().getSchemas());
    }
}
