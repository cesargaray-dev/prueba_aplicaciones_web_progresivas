package com.proyecto.servicios.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validación estricta para números telefónicos mexicanos de 10 dígitos
 * conforme al Plan Técnico Fundamental de Numeración Nacional (IFT).
 */
@Documented
@Constraint(validatedBy = TelefonoMexicanoValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface TelefonoMexicano {

    String message() default "El teléfono debe ser un número mexicano válido de 10 dígitos (sin prefijo 0 ni 1, ni secuencias/dígitos repetidos)";

    /**
     * Si es {@code true}, valores {@code null} o cadenas vacías se consideran válidos.
     */
    boolean optional() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
