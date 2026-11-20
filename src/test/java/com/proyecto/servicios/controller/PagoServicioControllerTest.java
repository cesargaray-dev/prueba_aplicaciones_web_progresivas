package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.AutenticacionException;
import com.proyecto.servicios.model.gestopago.*;
import com.proyecto.servicios.security.AccountOwnershipValidator;
import com.proyecto.servicios.service.NotificacionPagoService;
import com.proyecto.servicios.service.PagoServicioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoServicioControllerTest {

    @Mock
    private PagoServicioService pagoServicioService;

    @Mock
    private NotificacionPagoService notificacionPagoService;

    @Mock
    private AccountOwnershipValidator ownershipValidator;

    private PagoServicioController controller;

    @BeforeEach
    void setUp() {
        controller = new PagoServicioController(pagoServicioService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testGenerarCodigo2FA() {
        GenerarCodigo2FARequestDTO req = new GenerarCodigo2FARequestDTO("1234567890123456");
        GenerarCodigo2FAResponseDTO mockResp = GenerarCodigo2FAResponseDTO.builder()
                .codigo2FA("123456")
                .vigenciaSegundos(300L)
                .build();
        when(pagoServicioService.generarCodigo2FA(req)).thenReturn(mockResp);

        ResponseEntity<GenerarCodigo2FAResponseDTO> resp = controller.generarCodigo2FA(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("123456", resp.getBody().getCodigo2FA());
    }

    @Test
    void testValidarReferenciaEndpoint() {
        ValidarReferenciaRequestDTO req = ValidarReferenciaRequestDTO.builder()
                .idServicio(76)
                .idProducto(205)
                .referencia("5512345678")
                .build();
        ValidarReferenciaResponseDTO mockResp = ValidarReferenciaResponseDTO.builder()
                .esValida(true)
                .mensaje("Referencia válida")
                .tipoFront(1)
                .build();
        when(pagoServicioService.validarReferencia(req)).thenReturn(mockResp);

        ResponseEntity<ValidarReferenciaResponseDTO> resp = controller.validarReferencia(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().isEsValida());
        assertEquals("Referencia válida", resp.getBody().getMensaje());
    }

    @Test
    void testPagarServicioEndpoint() {
        PagoServicioRequestDTO req = PagoServicioRequestDTO.builder()
                .numeroCuenta("1234567890123456")
                .idServicio(76)
                .idProducto(205)
                .monto(new BigDecimal("20.00"))
                .codigo2FA("123456")
                .build();
        PagoServicioResponseDTO mockResp = PagoServicioResponseDTO.builder()
                .folioTicket("FT-001")
                .numeroAutorizacion("AUTH123")
                .montoPagado(new BigDecimal("20.00"))
                .build();
        when(pagoServicioService.procesarPagoServicio(req)).thenReturn(mockResp);

        ResponseEntity<PagoServicioResponseDTO> resp = controller.pagarServicio(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("FT-001", resp.getBody().getFolioTicket());
    }

    @Test
    void testConsultarHistorialEndpoint() {
        HistorialPagosRequestDTO req = new HistorialPagosRequestDTO("1234567890123456");
        HistorialPagosResponseDTO mockResp = HistorialPagosResponseDTO.builder()
                .numeroCuenta("1234567890123456")
                .totalMovimientos(0)
                .build();
        when(pagoServicioService.consultarHistorial(req)).thenReturn(mockResp);

        ResponseEntity<HistorialPagosResponseDTO> resp = controller.consultarHistorial(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(0, resp.getBody().getTotalMovimientos());
    }

    @Test
    void testReenviarComprobanteSinServicioLanzaIllegalState() {
        ReenviarComprobanteRequestDTO req = new ReenviarComprobanteRequestDTO("1234567890123456", "FT-1", null);
        assertThrows(IllegalStateException.class, () -> controller.reenviarComprobante(req));
    }

    @Test
    void testOwnershipValidatorEnController_rechazaSinAuthYCuentaAjena() {
        PagoServicioController securedController = new PagoServicioController(
                pagoServicioService, notificacionPagoService, ownershipValidator);

        GenerarCodigo2FARequestDTO req2fa = new GenerarCodigo2FARequestDTO("9999999999999999");
        assertThrows(AutenticacionException.class, () -> securedController.generarCodigo2FA(req2fa));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));
        assertThrows(AutenticacionException.class, () -> securedController.generarCodigo2FA(req2fa));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("cesar123", "tok", List.of()));
        doThrow(new AccessDeniedException("No tienes permisos")).when(ownershipValidator)
                .validarPropiedadCuenta("cesar123", "9999999999999999");

        assertThrows(AccessDeniedException.class, () -> securedController.generarCodigo2FA(req2fa));
        assertThrows(AccessDeniedException.class, () -> securedController.consultarHistorial(
                new HistorialPagosRequestDTO("9999999999999999")));
    }
}
