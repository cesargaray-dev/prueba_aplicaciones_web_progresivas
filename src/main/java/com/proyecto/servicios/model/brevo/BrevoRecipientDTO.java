package com.proyecto.servicios.model.brevo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrevoRecipientDTO {
    private String name;
    private String email;
}
