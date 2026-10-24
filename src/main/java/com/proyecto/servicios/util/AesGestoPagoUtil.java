package com.proyecto.servicios.util;

import com.proyecto.servicios.exception.GestoPagoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utilidad oficial de cifrado AES-256-CBC (PKCS5Padding) y generación de cadenas
 * aleatorias para la integración transaccional con GestoPago (PuntoRed).
 */
@Component
@Slf4j
public class AesGestoPagoUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int RANDOM_LENGTH = 10;
    private static final int DEFAULT_UPC_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${gestopago.api.secret-key:HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=}")
    private String defaultSecretKey;

    @Value("${gestopago.api.iv:p5ldmBPdd/9pjC0bDC/nSg==}")
    private String defaultIv;

    /**
     * Cifra el payload JSON utilizando la llave secreta e IV configurados en application.properties.
     *
     * @param plainJson Cadena JSON en texto plano.
     * @return Cadena cifrada codificada en Base64 para el campo 'signed'.
     */
    public String encrypt(String plainJson) {
        return encrypt(plainJson, defaultSecretKey, defaultIv);
    }

    /**
     * Descifra una cadena Base64 utilizando la llave secreta e IV configurados en application.properties.
     *
     * @param encryptedBase64 Texto cifrado en Base64.
     * @return Texto plano original.
     */
    public String decrypt(String encryptedBase64) {
        return decrypt(encryptedBase64, defaultSecretKey, defaultIv);
    }

    /**
     * Cifra una cadena en AES-256-CBC (PKCS5Padding) según la especificación técnica de PuntoRed.
     *
     * @param plainText       Texto plano (JSON) a cifrar.
     * @param secretKeyBase64 SECRET_KEY codificada en Base64 (32 bytes al decodificar).
     * @param ivBase64        IV codificado en Base64 (16 bytes al decodificar).
     * @return Resultado cifrado codificado en Base64.
     */
    public static String encrypt(String plainText, String secretKeyBase64, String ivBase64) {
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException("El payload a cifrar no puede ser nulo o vacío");
        }
        try {
            byte[] ivBytes = Base64.getDecoder().decode(ivBase64);
            byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);

            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            SecretKey secretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, ALGORITHM);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("Error al cifrar payload con AES-256-CBC para GestoPago", e);
            throw new GestoPagoException("Error al cifrar la petición para el proveedor de pagos", e, 500);
        }
    }

    /**
     * Descifra una cadena en AES-256-CBC (PKCS5Padding) según la especificación técnica de PuntoRed.
     *
     * @param cipherTextBase64 Texto cifrado en Base64.
     * @param secretKeyBase64  SECRET_KEY codificada en Base64.
     * @param ivBase64         IV codificado en Base64.
     * @return Texto plano descifrado.
     */
    public static String decrypt(String cipherTextBase64, String secretKeyBase64, String ivBase64) {
        if (cipherTextBase64 == null || cipherTextBase64.isBlank()) {
            throw new IllegalArgumentException("El texto cifrado a descifrar no puede ser nulo o vacío");
        }
        try {
            byte[] ivBytes = Base64.getDecoder().decode(ivBase64);
            byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);

            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            SecretKey secretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, ALGORITHM);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            byte[] decodedBytes = Base64.getDecoder().decode(cipherTextBase64);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error al descifrar payload con AES-256-CBC de GestoPago", e);
            throw new GestoPagoException("Error al descifrar los datos de GestoPago", e, 500);
        }
    }

    /**
     * Genera una cadena aleatoria alfanumérica de exactamente 10 caracteres
     * para el parámetro 'random' requerido en sendTx.do.
     *
     * @return String alfanumérico de 10 caracteres.
     */
    public String generarRandom10() {
        return generarCadenaAlfanumerica(RANDOM_LENGTH);
    }

    /**
     * Genera un identificador único de operación (UPC) alfanumérico
     * dentro del rango permitido por GestoPago (de 3 a 15 caracteres).
     *
     * @return String alfanumérico de 12 caracteres (ej: GP + 10 caracteres).
     */
    public String generarUpc() {
        return "GP" + generarCadenaAlfanumerica(DEFAULT_UPC_LENGTH - 2);
    }

    /**
     * Genera una cadena alfanumérica aleatoria de la longitud indicada.
     *
     * @param length Longitud exacta deseada.
     * @return Cadena alfanumérica aleatoria.
     */
    public static String generarCadenaAlfanumerica(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("La longitud debe ser mayor a cero");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = SECURE_RANDOM.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(index));
        }
        return sb.toString();
    }
}
