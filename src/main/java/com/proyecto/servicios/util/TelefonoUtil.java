package com.proyecto.servicios.util;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utilidades centralizadas de validación para números telefónicos mexicanos,
 * correos electrónicos y clasificación de productos de telefonía en GestoPago.
 */
public final class TelefonoUtil {

    private static final Pattern SOLO_DIEZ_DIGITOS = Pattern.compile("^\\d{10}$");
    private static final Pattern TELEFONO_MEXICO_PATTERN = Pattern.compile("^[2-9]\\d{9}$");
    private static final Pattern TODOS_DIGITOS_IGUALES = Pattern.compile("^(\\d)\\1{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_-][A-Za-z0-9+_.-]{0,63}@[A-Za-z0-9-][A-Za-z0-9.-]{0,188}\\.[A-Za-z]{2,12}$"
    );

    private static final Set<String> SECUENCIAS_INVALIDAS = Set.of(
            "0123456789",
            "1234567890",
            "9876543210",
            "0987654321",
            "2345678901",
            "1234512345",
            "5555555555"
    );

    /**
     * Categorías de GestoPago correspondientes exclusivamente a telefonía móvil,
     * paquetes y recargas celulares (idCatTipoServicio).
     */
    public static final Set<String> CATEGORIAS_TELEFONIA = Set.of("1", "2", "3", "4", "12", "13", "19");

    /**
     * Categorías de GestoPago con tipoFront=1 que corresponden a giftcards, boletos,
     * apuestas, TAGs de autopista o aportaciones (NO requieren teléfono celular obligatorio).
     */
    public static final Set<String> CATEGORIAS_FRONT1_NO_TELEFONIA = Set.of("8", "10", "11", "16", "18");

    private TelefonoUtil() {
    }

    /**
     * Valida que un número telefónico nacional mexicano cumpla:
     * <ul>
     *   <li>Exactamente 10 dígitos numéricos.</li>
     *   <li>Inicie con dígito de 2 a 9 (Plan Técnico de Numeración IFT México).</li>
     *   <li>No tenga todos los dígitos idénticos ni corresponda a secuencias triviales.</li>
     * </ul>
     *
     * @param raw Número telefónico a evaluar.
     * @return {@code true} si el número telefónico es válido.
     */
    public static boolean esTelefonoMexicanoValido(String raw) {
        if (raw == null) {
            return false;
        }
        String tel = raw.trim();
        if (!SOLO_DIEZ_DIGITOS.matcher(tel).matches()) {
            return false;
        }
        if (!TELEFONO_MEXICO_PATTERN.matcher(tel).matches()) {
            return false;
        }
        if (TODOS_DIGITOS_IGUALES.matcher(tel).matches()) {
            return false;
        }
        return !SECUENCIAS_INVALIDAS.contains(tel);
    }

    /**
     * Obtiene un mensaje descriptivo explicando por qué un teléfono no es válido,
     * o {@code null} si es válido.
     *
     * @param raw Número ingresado.
     * @return Mensaje de error o {@code null}.
     */
    public static String diagnosticarTelefonoInvalido(String raw) {
        if (raw == null || raw.isBlank()) {
            return "El número de teléfono es obligatorio y debe contener 10 dígitos.";
        }
        String tel = raw.trim();
        if (!SOLO_DIEZ_DIGITOS.matcher(tel).matches()) {
            return "El número de teléfono debe contener exactamente 10 dígitos numéricos";
        }
        if (!TELEFONO_MEXICO_PATTERN.matcher(tel).matches()) {
            return "El número telefónico en México debe iniciar con un dígito entre 2 y 9 (no puede iniciar con 0 ni 1).";
        }
        if (TODOS_DIGITOS_IGUALES.matcher(tel).matches() || SECUENCIAS_INVALIDAS.contains(tel)) {
            return "Ingresa un número telefónico real de 10 dígitos (no se aceptan dígitos repetidos ni secuencias como 1234567890).";
        }
        return null;
    }

    /**
     * Determina si un producto del catálogo de GestoPago corresponde realmente a una recarga
     * telefónica / paquete celular que exige un número móvil mexicano de 10 dígitos.
     *
     * @param tipoFront      Valor {@code frontType} del producto.
     * @param categoryId     Identificador de categoría {@code categoryId}.
     * @param nombreServicio Nombre del servicio/proveedor.
     * @return {@code true} si el producto requiere captura y validación de número celular.
     */
    public static boolean esRecargaTelefonica(Integer tipoFront, String categoryId, String nombreServicio) {
        if (tipoFront == null || tipoFront != 1) {
            return false;
        }
        if (categoryId != null && !categoryId.isBlank()) {
            String cat = categoryId.trim();
            if (CATEGORIAS_TELEFONIA.contains(cat)) {
                return true;
            }
            if (CATEGORIAS_FRONT1_NO_TELEFONIA.contains(cat)) {
                return false;
            }
        }
        if (nombreServicio != null) {
            String s = nombreServicio.toUpperCase();
            if (s.contains("AFORE") || s.contains("AMAZON") || s.contains("XBOX")
                    || s.contains("PLAYSTATION") || s.contains("NETFLIX") || s.contains("SPOTIFY")
                    || s.contains("YOUTUBE") || s.contains("NINTENDO") || s.contains("ACUARIO")
                    || s.contains("AZTLAN") || s.contains("KATAPLUM") || s.contains("RECORCHOLIS")
                    || s.contains("PASE") || s.contains("TELEVIA") || s.contains("EASYTRIP")
                    || s.contains("KINGSBET") || s.contains("AMESHOP") || s.contains("GOOGLE PLAY")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Valida sintaxis básica de correo electrónico.
     */
    public static boolean esCorreoSintaxisValida(String correo) {
        return correo != null && EMAIL_PATTERN.matcher(correo.trim()).matches();
    }

    /**
     * Indica si un correo pertenece a dominios reservados/placeholder sin buzón real.
     */
    public static boolean esCorreoPlaceholder(String correo) {
        if (correo == null || correo.isBlank()) {
            return true;
        }
        String lower = correo.trim().toLowerCase();
        return lower.endsWith("@ejemplo.com")
                || lower.endsWith(".ejemplo.com")
                || lower.endsWith("@example.com")
                || lower.endsWith(".example.com")
                || lower.endsWith("@test.com")
                || lower.endsWith(".local");
    }

    /**
     * Enmascara un correo para mostrarlo de forma segura en mensajes de UI y logs.
     */
    public static String enmascararCorreo(String correo) {
        if (correo == null || !correo.contains("@")) {
            return "***";
        }
        String trimmed = correo.trim();
        int atIdx = trimmed.indexOf('@');
        if (atIdx <= 2) {
            return trimmed.charAt(0) + "***" + trimmed.substring(atIdx);
        }
        return trimmed.substring(0, 3) + "***" + trimmed.substring(atIdx);
    }

    /**
     * Enmascara un número de cuenta dejando visibles únicamente los últimos 4 dígitos.
     */
    public static String enmascararCuenta(String numeroCuenta) {
        if (numeroCuenta == null || numeroCuenta.isBlank()) {
            return "****";
        }
        String trimmed = numeroCuenta.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "************" + trimmed.substring(trimmed.length() - 4);
    }
}
