package com.proyecto.servicios.util;

import com.proyecto.servicios.validation.TelefonoMexicano;
import com.proyecto.servicios.validation.TelefonoMexicanoValidator;
import jakarta.validation.Payload;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

class TelefonoUtilTest {

    @Test
    void esTelefonoMexicanoValido_aceptaNumerosRealesDeMexico() {
        assertTrue(TelefonoUtil.esTelefonoMexicanoValido("5512345678"));
        assertTrue(TelefonoUtil.esTelefonoMexicanoValido(" 8119876543 "));
        assertTrue(TelefonoUtil.esTelefonoMexicanoValido("3323456789"));
        assertNull(TelefonoUtil.diagnosticarTelefonoInvalido("5512345678"));
    }

    @Test
    void esTelefonoMexicanoValido_rechazaLongitudIncorrectaPrefijoRepetidosYSecuencias() {
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido(null));
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido("   "));
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido("55123"));
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido("55123456789"));
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido("55ABCDEFGH"));
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido("0512345678"));
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido("1512345678"));
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido("1111111111"));
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido("0000000000"));
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido("5555555555"));
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido("1234567890"));
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido("0123456789"));
        assertFalse(TelefonoUtil.esTelefonoMexicanoValido("9876543210"));

        assertTrue(TelefonoUtil.diagnosticarTelefonoInvalido(null).contains("obligatorio"));
        assertTrue(TelefonoUtil.diagnosticarTelefonoInvalido("55123").contains("10 dígitos"));
        assertTrue(TelefonoUtil.diagnosticarTelefonoInvalido("1512345678").contains("entre 2 y 9"));
        assertTrue(TelefonoUtil.diagnosticarTelefonoInvalido("2222222222").contains("telefónico real"));
        assertTrue(TelefonoUtil.diagnosticarTelefonoInvalido("2345678901").contains("secuencias"));
    }

    @Test
    void esRecargaTelefonica_clasificaPorTipoFrontCategoriaYNombre() {
        assertTrue(TelefonoUtil.esRecargaTelefonica(1, "2", "Telcel"));
        assertTrue(TelefonoUtil.esRecargaTelefonica(1, "13", "ABIB"));
        assertTrue(TelefonoUtil.esRecargaTelefonica(1, null, "Telcel"));

        assertFalse(TelefonoUtil.esRecargaTelefonica(1, "10", "Amazon Cash"));
        assertFalse(TelefonoUtil.esRecargaTelefonica(1, "8", "Afore"));
        assertFalse(TelefonoUtil.esRecargaTelefonica(1, "11", "Kataplum"));
        assertFalse(TelefonoUtil.esRecargaTelefonica(1, "16", "KingsBet"));
        assertFalse(TelefonoUtil.esRecargaTelefonica(1, "18", "PASE TAG"));
        assertFalse(TelefonoUtil.esRecargaTelefonica(1, null, "Xbox Game Pass"));
        assertFalse(TelefonoUtil.esRecargaTelefonica(1, null, "PASE Autopistas"));
        assertFalse(TelefonoUtil.esRecargaTelefonica(2, "2", "Telcel Recibo"));
        assertFalse(TelefonoUtil.esRecargaTelefonica(null, "1", "Movistar"));
    }

    @Test
    void utilidadesCorreo_validanPlaceholderYEnmascaran() {
        assertTrue(TelefonoUtil.esCorreoSintaxisValida("cliente.real@gmail.com"));
        assertFalse(TelefonoUtil.esCorreoSintaxisValida("invalido"));
        assertFalse(TelefonoUtil.esCorreoSintaxisValida(null));

        assertTrue(TelefonoUtil.esCorreoPlaceholder("demo@ejemplo.com"));
        assertTrue(TelefonoUtil.esCorreoPlaceholder("user@example.com"));
        assertTrue(TelefonoUtil.esCorreoPlaceholder("qa@test.com"));
        assertTrue(TelefonoUtil.esCorreoPlaceholder(""));
        assertFalse(TelefonoUtil.esCorreoPlaceholder("cesar@gmail.com"));

        assertEquals("ces***@gmail.com", TelefonoUtil.enmascararCorreo("cesar@gmail.com"));
        assertEquals("a***@gmail.com", TelefonoUtil.enmascararCorreo("ab@gmail.com"));
        assertEquals("***", TelefonoUtil.enmascararCorreo(null));
    }

    @Test
    void telefonoMexicanoValidator_respetaOpcionalYRequerido() {
        TelefonoMexicanoValidator requiredValidator = new TelefonoMexicanoValidator();
        requiredValidator.initialize(crearAnotacion(false));
        assertTrue(requiredValidator.isValid("5512345678", null));
        assertFalse(requiredValidator.isValid(null, null));
        assertFalse(requiredValidator.isValid("1111111111", null));

        TelefonoMexicanoValidator optionalValidator = new TelefonoMexicanoValidator();
        optionalValidator.initialize(crearAnotacion(true));
        assertTrue(optionalValidator.isValid(null, null));
        assertTrue(optionalValidator.isValid("  ", null));
        assertFalse(optionalValidator.isValid("1234567890", null));
    }

    private TelefonoMexicano crearAnotacion(boolean optional) {
        return new TelefonoMexicano() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return TelefonoMexicano.class;
            }

            @Override
            public String message() {
                return "Teléfono inválido";
            }

            @Override
            public boolean optional() {
                return optional;
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends Payload>[] payload() {
                return new Class[0];
            }
        };
    }
}
