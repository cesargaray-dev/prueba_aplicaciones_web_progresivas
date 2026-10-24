package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.Autenticacion2FAException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Clase base abstracta para la generación, propagación y validación de códigos 2FA
 * temporales (OTP de 6 dígitos numéricos) exclusivos para autorizar pagos de servicios.
 * <p>
 * El código generado es temporal (vigencia por defecto de 5 minutos y de un solo uso),
 * almacenándose en Redis con fallback automático en memoria para garantizar disponibilidad.
 */
@Slf4j
public abstract class Soporte2FAService {

    protected static final String REDIS_2FA_PREFIX = "gestopago:2fa:cuenta:";
    protected static final long VIGENCIA_SEGUNDOS_DEFAULT = 300L; // 5 minutos
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Almacenamiento temporal en memoria (fallback y propagación directa en el servicio) */
    private final Map<String, CodigoTemporal2FA> codigosTemporales = new ConcurrentHashMap<>();

    private final RedisTemplate<String, String> redisTemplate;

    protected Soporte2FAService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Genera un nuevo código 2FA numérico de 6 dígitos y lo propaga temporalmente
     * para la cuenta indicada.
     *
     * @param numeroCuenta Número de cuenta bancaria de 16 dígitos.
     * @return Objeto {@link CodigoTemporal2FA} con el código generado y su fecha de expiración.
     */
    protected CodigoTemporal2FA generarYPropagarCodigo2FA(String numeroCuenta) {
        int numero = SECURE_RANDOM.nextInt(900_000) + 100_000; // Siempre 6 dígitos (100000 - 999999)
        String codigo = String.valueOf(numero);
        return propagarCodigo2FA(numeroCuenta, codigo);
    }

    /**
     * Propaga un código 2FA específico de 6 dígitos de forma temporal para la cuenta.
     *
     * @param numeroCuenta Número de cuenta bancaria.
     * @param codigo2FA    Código de 6 dígitos numéricos.
     * @return Objeto {@link CodigoTemporal2FA} registrado.
     */
    public CodigoTemporal2FA propagarCodigo2FA(String numeroCuenta, String codigo2FA) {
        LocalDateTime expiracion = LocalDateTime.now().plusSeconds(VIGENCIA_SEGUNDOS_DEFAULT);
        CodigoTemporal2FA registro = new CodigoTemporal2FA(codigo2FA, expiracion);
        codigosTemporales.put(numeroCuenta, registro);

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(
                        REDIS_2FA_PREFIX + numeroCuenta,
                        codigo2FA,
                        VIGENCIA_SEGUNDOS_DEFAULT,
                        TimeUnit.SECONDS
                );
            } catch (Exception e) {
                log.debug("Redis no disponible para guardar 2FA temporal, se mantiene en memoria local para la cuenta {}", numeroCuenta);
            }
        }

        log.info("Código 2FA temporal generado y propagado para la cuenta {} (vigencia: {}s)",
                numeroCuenta, VIGENCIA_SEGUNDOS_DEFAULT);
        return registro;
    }

    /**
     * Valida que el código 2FA proporcionado coincida con el código temporal activo
     * de la cuenta bancaria. Si no existe, expiró o no coincide, lanza {@link Autenticacion2FAException}.
     *
     * @param numeroCuenta   Número de cuenta de 16 dígitos.
     * @param codigoRecibido Código 2FA de 6 dígitos enviado en la petición de pago.
     */
    protected void validarCodigo2FA(String numeroCuenta, String codigoRecibido) {
        if (codigoRecibido == null || !codigoRecibido.matches("^\\d{6}$")) {
            throw new Autenticacion2FAException("El código 2FA proporcionado no tiene un formato válido de 6 dígitos.");
        }

        String codigoEsperado = obtenerCodigoActivo(numeroCuenta);

        if (codigoEsperado == null) {
            throw new Autenticacion2FAException(
                    "No existe un código 2FA temporal activo o ha expirado para la cuenta " + numeroCuenta +
                    ". Solicite un nuevo código 2FA antes de realizar el pago.");
        }

        if (!codigoEsperado.equals(codigoRecibido)) {
            throw new Autenticacion2FAException(
                    "Código de autenticación 2FA incorrecto para la cuenta " + numeroCuenta + ". Operación rechazada.");
        }
    }

    /**
     * Consume e invalida el código 2FA temporal una vez que ha sido utilizado.
     *
     * @param numeroCuenta Número de cuenta bancaria.
     */
    protected void consumirCodigo2FA(String numeroCuenta) {
        codigosTemporales.remove(numeroCuenta);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(REDIS_2FA_PREFIX + numeroCuenta);
            } catch (Exception e) {
                log.debug("No se pudo eliminar clave 2FA de Redis para la cuenta {}", numeroCuenta);
            }
        }
    }

    private String obtenerCodigoActivo(String numeroCuenta) {
        // 1. Verificar en memoria local primero (con chequeo de expiración)
        CodigoTemporal2FA local = codigosTemporales.get(numeroCuenta);
        if (local != null) {
            if (LocalDateTime.now().isBefore(local.getFechaExpiracion())) {
                return local.getCodigo();
            } else {
                codigosTemporales.remove(numeroCuenta);
            }
        }

        // 2. Verificar en Redis si está disponible
        if (redisTemplate != null) {
            try {
                String codigoRedis = redisTemplate.opsForValue().get(REDIS_2FA_PREFIX + numeroCuenta);
                if (codigoRedis != null && !codigoRedis.isBlank()) {
                    return codigoRedis;
                }
            } catch (Exception e) {
                log.debug("Redis no disponible al consultar código 2FA para la cuenta {}", numeroCuenta);
            }
        }

        return null;
    }

    @Getter
    @AllArgsConstructor
    public static class CodigoTemporal2FA {
        private final String codigo;
        private final LocalDateTime fechaExpiracion;
    }
}
