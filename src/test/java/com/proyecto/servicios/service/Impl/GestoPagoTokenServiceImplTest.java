package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GestoPagoTokenServiceImplTest {

    @Mock
    private GestoPagoAuthClient authClient;

    @Mock
    private GestoPagoTokenRepository tokenRepository;

    @Mock
    private GestoPagoTokenMapper tokenMapper;

    private GestoPagoTokenServiceImpl tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new GestoPagoTokenServiceImpl(authClient, tokenRepository, tokenMapper);
        ReflectionTestUtils.setField(tokenService, "idDistribuidor", 83);
        ReflectionTestUtils.setField(tokenService, "codigoDispositivo", "GPS83-TPV-17");
        ReflectionTestUtils.setField(tokenService, "password", "12345678");
    }

    @Test
    void renovarToken_exitoso_actualizaTokenExistente() {
        GestoPagoAuthResponse authResponse = new GestoPagoAuthResponse();
        authResponse.setToken("nuevo-jwt-token");
        authResponse.setExpiresIn(3600L);

        GestoPagoToken existingToken = new GestoPagoToken();
        existingToken.setId(1);
        existingToken.setToken("token-viejo");

        when(authClient.authenticate(83, "GPS83-TPV-17", "12345678")).thenReturn(authResponse);
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(83, "GPS83-TPV-17"))
                .thenReturn(Optional.of(existingToken));

        tokenService.renovarToken();

        verify(tokenMapper, times(1)).updateEntity(authResponse, existingToken);
        verify(tokenRepository, times(1)).save(existingToken);
    }

    @Test
    void renovarToken_exitoso_creaNuevoTokenSiNoExiste() {
        GestoPagoAuthResponse authResponse = new GestoPagoAuthResponse();
        authResponse.setToken("nuevo-jwt-token");

        GestoPagoToken nuevoToken = new GestoPagoToken();

        when(authClient.authenticate(83, "GPS83-TPV-17", "12345678")).thenReturn(authResponse);
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(83, "GPS83-TPV-17"))
                .thenReturn(Optional.empty());
        when(tokenMapper.toEntity(authResponse)).thenReturn(nuevoToken);

        tokenService.renovarToken();

        verify(tokenRepository, times(1)).save(nuevoToken);
        assertEquals(83, nuevoToken.getIdDistribuidor());
        assertEquals("GPS83-TPV-17", nuevoToken.getCodigoDispositivo());
        assertTrue(nuevoToken.getActivo());
    }

    @Test
    void renovarToken_respuestaNula_noGuardaNada() {
        when(authClient.authenticate(83, "GPS83-TPV-17", "12345678")).thenReturn(null);

        tokenService.renovarToken();

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void renovarToken_conExcepcion_manejaErrorYNoFalla() {
        when(authClient.authenticate(anyInt(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Connection timeout"));

        assertDoesNotThrow(() -> tokenService.renovarToken());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void obtenerTokenActivo_retornaTokenDeRepositorio() {
        GestoPagoToken token = new GestoPagoToken();
        token.setToken("token-activo");

        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(83, "GPS83-TPV-17"))
                .thenReturn(Optional.of(token));

        Optional<GestoPagoToken> result = tokenService.obtenerTokenActivo(83, "GPS83-TPV-17");

        assertTrue(result.isPresent());
        assertEquals("token-activo", result.get().getToken());
    }
}

