package com.proyecto.servicios.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Cliente Feign para consumir los endpoints de productos y transacciones de GestoPago.
 * Retorna la respuesta como String para manejar tanto XML como JSON,
 * ya que el servicio externo puede responder en text/xml.
 */
@FeignClient(name = "gestoPagoProductClient", url = "${gestopago.api.url}")
public interface GestoPagoProductClient {

    @GetMapping(value = "/sistema/service/getProductList.do", produces = "*/*")
    String getProductList(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-API-Key") String apiKey
    );

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

    default String sendTx(String random, String signed, String token, String apiKey) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("random", random);
        formData.add("signed", signed);
        return sendTxForm(formData, token, apiKey);
    }
}
