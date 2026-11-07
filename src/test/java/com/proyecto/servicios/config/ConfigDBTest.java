package com.proyecto.servicios.config;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigDBTest {

    @Mock
    private Environment environment;

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Test
    void sfDatasource_creaDataSourceConPropiedadesDeEntorno() {
        ConfigDB configDB = new ConfigDB();
        ReflectionTestUtils.setField(configDB, "env", environment);

        when(environment.getProperty("spring.datasource.url")).thenReturn("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        when(environment.getProperty("spring.datasource.username")).thenReturn("sa");
        when(environment.getProperty("spring.datasource.password")).thenReturn("");

        DataSource dataSource = configDB.sfDatasource();

        assertNotNull(dataSource);
    }

    @Test
    void sfDatasource_conErrorRetornaNull() {
        ConfigDB configDB = new ConfigDB();
        ReflectionTestUtils.setField(configDB, "env", null);

        DataSource dataSource = configDB.sfDatasource();
        assertNull(dataSource);
    }

    @Test
    void sfEntityManagerFactory_construyeLocalContainerEntityManagerFactoryBean() {
        ConfigDB configDB = new ConfigDB();
        ReflectionTestUtils.setField(configDB, "env", environment);

        when(environment.getProperty("spring.datasource.url")).thenReturn("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        when(environment.getProperty("spring.datasource.username")).thenReturn("sa");
        when(environment.getProperty("spring.datasource.password")).thenReturn("");

        LocalContainerEntityManagerFactoryBean em = configDB.sfEntityManagerFactory();

        assertNotNull(em);
        assertEquals("sfDatasource", em.getPersistenceUnitName());
    }

    @Test
    void sfTransactionManager_creaJpaTransactionManager() {
        ConfigDB configDB = new ConfigDB();

        PlatformTransactionManager tm = configDB.sfTransactionManager(entityManagerFactory);

        assertNotNull(tm);
        assertTrue(tm instanceof JpaTransactionManager);
        assertSame(entityManagerFactory, ((JpaTransactionManager) tm).getEntityManagerFactory());
    }
}

