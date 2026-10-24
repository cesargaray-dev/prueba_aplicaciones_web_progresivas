package com.proyecto.servicios.model.brevo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrevoEmailRequestDTO {
    private BrevoSenderDTO sender;
    private List<BrevoRecipientDTO> to;
    private String subject;
    private String htmlContent;
}
