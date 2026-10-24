package com.proyecto.servicios.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Cliente Feign para ejecutar transacciones de pago de servicios (sendTx.do) en GestoPago (PuntoRed).
 */
@FeignClient(name = "gestoPagoTxClient", url = "${gestopago.api.url}")
public interface GestoPagoTxClient {

    @PostMapping(
            value = "/sistema/service/sendTx.do",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = "*/*"
    )
    String sendTxForm(
            @RequestBody MultiValueMap<String, String> formParams,
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-API-Key") String apiKey
    );

    /**
     * Envía una transacción cifrada a GestoPago mediante POST application/x-www-form-urlencoded.
     *
     * @param random Cadena alfanumérica aleatoria de 10 caracteres.
     * @param signed Payload JSON cifrado con AES-256-CBC en Base64.
     * @param token  Header Authorization ("Bearer <JWT>").
     * @param apiKey Header X-API-Key.
     * @return Respuesta XML cruda devuelta por GestoPago.
     */
    default String sendTx(String random, String signed, String token, String apiKey) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("random", random);
        formData.add("signed", signed);
        return sendTxForm(formData, token, apiKey);
    }
}
