package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.Autenticacion2FAException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Soporte2FAServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private Concrete2FAService soporte2FAService;
    private static final String CUENTA = "1234567890123456";

    static class Concrete2FAService extends Soporte2FAService {
        public Concrete2FAService(RedisTemplate<String, String> redisTemplate) {
            super(redisTemplate);
        }

        public void invocarValidar(String cuenta, String codigo) {
            validarCodigo2FA(cuenta, codigo);
        }

        public CodigoTemporal2FA invocarGenerar(String cuenta) {
            return generarYPropagarCodigo2FA(cuenta);
        }
    }

    @BeforeEach
    void setUp() {
        soporte2FAService = new Concrete2FAService(redisTemplate);
    }

    @Test
    void generarYPropagarCodigo2FA_generaCodigo6DigitosYGuardaEnMemoria() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Soporte2FAService.CodigoTemporal2FA result = soporte2FAService.invocarGenerar(CUENTA);

        assertNotNull(result);
        assertNotNull(result.getCodigo());
        assertTrue(result.getCodigo().matches("^\\d{6}$"));
        assertTrue(result.getFechaExpiracion().isAfter(LocalDateTime.now()));
        verify(valueOperations, times(1)).set(anyString(), eq(result.getCodigo()), anyLong(), any());
    }

    @Test
    void validarCodigo2FA_conCodigoValido_noLanzaExcepcion() {
        soporte2FAService.propagarCodigo2FA(CUENTA, "654321");

        assertDoesNotThrow(() -> soporte2FAService.invocarValidar(CUENTA, "654321"));
    }

    @Test
    void validarCodigo2FA_conFormatoInvalido_lanzaAutenticacion2FAException() {
        assertThrows(Autenticacion2FAException.class,
                () -> soporte2FAService.invocarValidar(CUENTA, "123"));
        assertThrows(Autenticacion2FAException.class,
                () -> soporte2FAService.invocarValidar(CUENTA, "ABCDEF"));
        assertThrows(Autenticacion2FAException.class,
                () -> soporte2FAService.invocarValidar(CUENTA, null));
    }

    @Test
    void validarCodigo2FA_codigoNoExisteOExpirado_lanzaAutenticacion2FAException() {
        assertThrows(Autenticacion2FAException.class,
                () -> soporte2FAService.invocarValidar(CUENTA, "123456"));
    }

    @Test
    void validarCodigo2FA_codigoIncorrecto_lanzaAutenticacion2FAException() {
        soporte2FAService.propagarCodigo2FA(CUENTA, "111111");

        assertThrows(Autenticacion2FAException.class,
                () -> soporte2FAService.invocarValidar(CUENTA, "999999"));
    }

    @Test
    void consumirCodigo2FA_eliminaCodigoDeMemoriaYRedis() {
        when(redisTemplate.delete(anyString())).thenReturn(true);
        soporte2FAService.propagarCodigo2FA(CUENTA, "112233");

        soporte2FAService.consumirCodigo2FA(CUENTA);

        assertThrows(Autenticacion2FAException.class,
                () -> soporte2FAService.invocarValidar(CUENTA, "112233"));
        verify(redisTemplate, times(1)).delete(anyString());
    }
}

