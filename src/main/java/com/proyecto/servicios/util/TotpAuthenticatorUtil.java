package com.proyecto.servicios.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Utilidad oficial compatible con Google Authenticator (TOTP RFC 6238 / Base32 RFC 4648),
 * generación de código QR PNG y construcción de leyendas oficiales de comprobantes.
 */
public final class TotpAuthenticatorUtil {

    public static final String EMISOR_DEFAULT = "Banco Digital";
    public static final int DIGITOS_TOTP = 6;
    public static final int PERIODO_SEGUNDOS = 30;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TotpAuthenticatorUtil() {
    }

    /**
     * Genera una clave secreta aleatoria de 20 bytes (160 bits) codificada en Base32 (32 caracteres).
     */
    public static String generarSecretoBase32() {
        byte[] bytes = new byte[20];
        SECURE_RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    /**
     * Formatea el secreto Base32 en bloques de 4 caracteres separados por espacio para captura manual.
     */
    public static String formatearSecretoLegible(String secret) {
        String limpio = normalizarSecretoBase32(secret);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limpio.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append(' ');
            }
            sb.append(limpio.charAt(i));
        }
        return sb.toString();
    }

    public static String normalizarSecretoBase32(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[\\s\\-=]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean esSecretoBase32Valido(String raw) {
        String limpio = normalizarSecretoBase32(raw);
        if (limpio.length() < 16 || limpio.length() > 128) {
            return false;
        }
        for (int i = 0; i < limpio.length(); i++) {
            if (BASE32_ALPHABET.indexOf(limpio.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Construye la URI otpauth://totp/... estándar reconocida por Google Authenticator.
     */
    public static String construirOtpAuthUri(String emisor, String cuenta, String secretBase32) {
        String issuerClean = (emisor == null || emisor.isBlank()) ? EMISOR_DEFAULT : emisor.trim();
        String accountClean = (cuenta == null || cuenta.isBlank()) ? "cliente" : cuenta.trim();
        String secretClean = normalizarSecretoBase32(secretBase32);

        String label = urlEncodePath(issuerClean) + ":" + urlEncodePath(accountClean);
        return "otpauth://totp/" + label
                + "?secret=" + secretClean
                + "&issuer=" + urlEncodeQuery(issuerClean)
                + "&algorithm=SHA1"
                + "&digits=" + DIGITOS_TOTP
                + "&period=" + PERIODO_SEGUNDOS;
    }

    /**
     * Genera una imagen PNG en Base64 del código QR lista para escanear con Google Authenticator.
     */
    public static String generarQrPngBase64(String contenido, int sizePx) {
        try {
            int dimension = Math.max(200, Math.min(sizePx, 600));
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(contenido, BarcodeFormat.QR_CODE, dimension, dimension, hints);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible generar el código QR para Google Authenticator", e);
        }
    }

    /**
     * Genera el código TOTP de 6 dígitos para el instante indicado (en segundos Unix).
     */
    public static String generarCodigoTotpEnInstante(String secretBase32, long epochSeconds) {
        byte[] key = decodeBase32(normalizarSecretoBase32(secretBase32));
        long timeStep = Math.floorDiv(epochSeconds, PERIODO_SEGUNDOS);
        return calcularHotp(key, timeStep);
    }

    public static String generarCodigoTotpActual(String secretBase32) {
        return generarCodigoTotpEnInstante(secretBase32, Instant.now().getEpochSecond());
    }

    /**
     * Verifica un código de 6 dígitos contra el secreto Base32 aceptando ±1 ventana de 30s por desfase de reloj.
     */
    public static boolean verificarCodigo(String secretBase32, String codigoRecibido) {
        return verificarCodigoEnInstante(secretBase32, codigoRecibido, Instant.now().getEpochSecond(), 1);
    }

    public static boolean verificarCodigoEnInstante(
            String secretBase32, String codigoRecibido, long epochSeconds, int ventanaPasos) {
        if (codigoRecibido == null) {
            return false;
        }
        String code = codigoRecibido.trim();
        if (!code.matches("^\\d{6}$") || !esSecretoBase32Valido(secretBase32)) {
            return false;
        }
        byte[] key = decodeBase32(normalizarSecretoBase32(secretBase32));
        if (key.length == 0) {
            return false;
        }
        long currentStep = Math.floorDiv(epochSeconds, PERIODO_SEGUNDOS);
        int window = Math.max(0, Math.min(ventanaPasos, 2));
        for (int offset = -window; offset <= window; offset++) {
            String candidate = calcularHotp(key, currentStep + offset);
            if (comparacionTiempoConstante(candidate, code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Construye la leyenda oficial de aclaraciones para mostrarse en el ticket Web, App Móvil, PDF y Correo.
     */
    public static String construirLeyendaTicket(
            String estadoOperacion,
            String leyendaProveedorRaw,
            String numAutorizacion,
            String folioTicket) {
        String notaProveedor = limpiarHtmlLeyenda(leyendaProveedorRaw);
        String auth = (numAutorizacion != null && !numAutorizacion.isBlank()) ? numAutorizacion.trim() : "N/A";
        String folio = (folioTicket != null && !folioTicket.isBlank()) ? folioTicket.trim() : "N/A";
        String prefijoProveedor = notaProveedor.isEmpty() ? "" : (notaProveedor + " — ");

        if ("RECHAZADA".equalsIgnoreCase(estadoOperacion)) {
            return prefijoProveedor
                    + "Operación no aplicada: no se realizó ningún cargo a su cuenta. "
                    + "Conserve este comprobante digital como constancia del intento de operación. "
                    + "Para cualquier duda o aclaración comuníquese a nuestra Línea de Aclaraciones al 800-226-2600 "
                    + "(sin costo, las 24 horas) o envíe un correo a aclaraciones@bancodigital.com "
                    + "mencionando su Folio de Ticket: " + folio + ".";
        }
        if ("PENDIENTE".equalsIgnoreCase(estadoOperacion)) {
            return prefijoProveedor
                    + "Operación pendiente de confirmación con el proveedor. Verifique su historial antes de reintentar. "
                    + "Para aclaraciones comuníquese al 800-226-2600 o a aclaraciones@bancodigital.com "
                    + "mencionando el Folio de Ticket: " + folio + ".";
        }
        return prefijoProveedor
                + "Conserve este comprobante digital como constancia oficial de pago. "
                + "Para cualquier duda, aclaración, reporte de saldo o reclamación relacionada con esta transacción "
                + "o la aplicación del servicio por parte del proveedor, comuníquese a nuestra Línea de Aclaraciones "
                + "al 800-226-2600 (sin costo, las 24 horas) o envíe un correo a aclaraciones@bancodigital.com "
                + "dentro de los 30 días naturales posteriores a la fecha de operación. "
                + "Favor de mencionar el Número de Autorización: " + auth + " y su Folio de Ticket: " + folio + ".";
    }

    public static String limpiarHtmlLeyenda(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String sinTags = raw
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sinTags.endsWith(".") ? sinTags.substring(0, sinTags.length() - 1).trim() : sinTags;
    }

    private static String calcularHotp(byte[] key, long counter) {
        try {
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Error calculando código TOTP RFC 6238", e);
        }
    }

    private static boolean comparacionTiempoConstante(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    static String encodeBase32(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder result = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = data[0] & 0xFF;
        int next = 1;
        int bitsLeft = 8;
        while (bitsLeft > 0 || next < data.length) {
            if (bitsLeft < 5) {
                if (next < data.length) {
                    buffer <<= 8;
                    buffer |= (data[next++] & 0xFF);
                    bitsLeft += 8;
                } else {
                    int pad = 5 - bitsLeft;
                    buffer <<= pad;
                    bitsLeft += pad;
                }
            }
            int index = (buffer >> (bitsLeft - 5)) & 0x1F;
            bitsLeft -= 5;
            result.append(BASE32_ALPHABET.charAt(index));
        }
        return result.toString();
    }

    static byte[] decodeBase32(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(encoded.length() * 5 / 8);
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < encoded.length(); i++) {
            int val = BASE32_ALPHABET.indexOf(encoded.charAt(i));
            if (val < 0) {
                continue;
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static String urlEncodePath(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String urlEncodeQuery(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
