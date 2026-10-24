package com.proyecto.servicios.client;

import com.proyecto.servicios.model.brevo.BrevoEmailRequestDTO;
import com.proyecto.servicios.model.brevo.BrevoEmailResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Cliente OpenFeign para la API REST v3 de Brevo (Sendinblue).
 */
@FeignClient(name = "brevoEmailClient", url = "${brevo.api.url:https://api.brevo.com}")
public interface BrevoEmailClient {

    @PostMapping(
            value = "/v3/smtp/email",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    BrevoEmailResponseDTO sendEmail(
            @RequestHeader("api-key") String apiKey,
            @RequestBody BrevoEmailRequestDTO request
    );
}
