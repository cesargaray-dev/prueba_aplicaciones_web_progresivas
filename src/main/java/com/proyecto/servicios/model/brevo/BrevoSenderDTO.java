package com.proyecto.servicios.model.brevo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrevoSenderDTO {
    private String name;
    private String email;
}
