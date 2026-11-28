package com.proyecto.servicios.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TotpAuthenticatorUtilTest {

    @Test
    void generaSecretoValidoQrPngYVerificaCodigoTotpRfc6238() {
        String secret = TotpAuthenticatorUtil.generarSecretoBase32();
        assertNotNull(secret);
        assertEquals(32, secret.length());
        assertTrue(TotpAuthenticatorUtil.esSecretoBase32Valido(secret));
        assertTrue(TotpAuthenticatorUtil.formatearSecretoLegible(secret).contains(" "));

        String uri = TotpAuthenticatorUtil.construirOtpAuthUri("Banco Digital", "carlos_utng", secret);
        assertTrue(uri.startsWith("otpauth://totp/Banco%20Digital:carlos_utng?secret=" + secret));
        assertTrue(uri.contains("period=30"));
        assertTrue(uri.contains("digits=6"));

        String qrBase64 = TotpAuthenticatorUtil.generarQrPngBase64(uri, 240);
        assertNotNull(qrBase64);
        byte[] pngBytes = Base64.getDecoder().decode(qrBase64);
        assertTrue(pngBytes.length > 200);
        // Firma mágica PNG: 0x89 'P' 'N' 'G'
        assertEquals((byte) 0x89, pngBytes[0]);
        assertEquals((byte) 'P', pngBytes[1]);
        assertEquals((byte) 'N', pngBytes[2]);
        assertEquals((byte) 'G', pngBytes[3]);

        long fixedEpoch = 1_700_000_000L;
        String codeNow = TotpAuthenticatorUtil.generarCodigoTotpEnInstante(secret, fixedEpoch);
        assertTrue(codeNow.matches("^\\d{6}$"));
        assertTrue(TotpAuthenticatorUtil.verificarCodigoEnInstante(secret, codeNow, fixedEpoch, 1));
        assertTrue(TotpAuthenticatorUtil.verificarCodigoEnInstante(secret, codeNow, fixedEpoch + 29, 1));
        assertFalse(TotpAuthenticatorUtil.verificarCodigoEnInstante(secret, "000000".equals(codeNow) ? "999999" : "000000", fixedEpoch, 0));
    }

    @Test
    void construyeLeyendaTicketParaExitosaYRechazada() {
        String rechazada = TotpAuthenticatorUtil.construirLeyendaTicket(
                "RECHAZADA", "<br>Abono inmediato</br>", "-1", "FT-20261127-ABCD");
        assertTrue(rechazada.contains("Abono inmediato"));
        assertTrue(rechazada.contains("no se realizó ningún cargo a su cuenta"));
        assertTrue(rechazada.contains("-1"));
        assertTrue(rechazada.contains("FT-20261127-ABCD"));

        String exitosa = TotpAuthenticatorUtil.construirLeyendaTicket(
                "EXITOSA", null, "998877", "TK-12345");
        assertTrue(exitosa.contains("800-226-2600"));
        assertTrue(exitosa.contains("998877"));
        assertTrue(exitosa.contains("TK-12345"));
    }
}
