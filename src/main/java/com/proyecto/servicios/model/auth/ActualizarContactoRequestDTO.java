package com.proyecto.servicios.model.auth;

import com.proyecto.servicios.validation.TelefonoMexicano;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud autenticada para actualizar datos personales y de contacto del cliente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Actualización de datos personales y de contacto del perfil autenticado")
public class ActualizarContactoRequestDTO {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 100, message = "El correo electrónico no debe exceder 100 caracteres")
    private String correo;

    @NotBlank(message = "El teléfono móvil es obligatorio")
    @TelefonoMexicano
    private String telefonoMovil;

    @Pattern(regexp = "^(?=.*[A-Za-zÁÉÍÓÚÜáéíóúüÑñ])[A-Za-zÁÉÍÓÚÜáéíóúüÑñ\\s]+$",
            message = "El nombre solo debe contener letras")
    @Size(min = 2, max = 80, message = "El nombre debe tener entre 2 y 80 caracteres")
    @Schema(description = "Primer nombre del titular (opcional)", example = "César")
    private String nombre;

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚÜáéíóúüÑñ\\s]*$",
            message = "El segundo nombre solo debe contener letras")
    @Size(max = 80, message = "El segundo nombre no debe exceder 80 caracteres")
    @Schema(description = "Segundo nombre del titular (opcional)", example = "Enrique")
    private String segundoNombre;

    @Pattern(regexp = "^(?=.*[A-Za-zÁÉÍÓÚÜáéíóúüÑñ])[A-Za-zÁÉÍÓÚÜáéíóúüÑñ\\s]+$",
            message = "El apellido paterno solo debe contener letras")
    @Size(min = 2, max = 80, message = "El apellido paterno debe tener entre 2 y 80 caracteres")
    @Schema(description = "Apellido paterno del titular (opcional)", example = "Garay")
    private String apellidoPaterno;

    @Pattern(regexp = "^(?=.*[A-Za-zÁÉÍÓÚÜáéíóúüÑñ])[A-Za-zÁÉÍÓÚÜáéíóúüÑñ\\s]+$",
            message = "El apellido materno solo debe contener letras")
    @Size(min = 2, max = 80, message = "El apellido materno debe tener entre 2 y 80 caracteres")
    @Schema(description = "Apellido materno del titular (opcional)", example = "García")
    private String apellidoMaterno;

    public ActualizarContactoRequestDTO(String correo, String telefonoMovil) {
        this.correo = correo;
        this.telefonoMovil = telefonoMovil;
    }
}
