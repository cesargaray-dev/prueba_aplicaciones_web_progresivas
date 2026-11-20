package com.proyecto.servicios.validation;

import com.proyecto.servicios.util.TelefonoUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador Jakarta para {@link TelefonoMexicano}.
 */
public class TelefonoMexicanoValidator implements ConstraintValidator<TelefonoMexicano, String> {

    private boolean optional;

    @Override
    public void initialize(TelefonoMexicano constraintAnnotation) {
        this.optional = constraintAnnotation.optional();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return optional;
        }
        return TelefonoUtil.esTelefonoMexicanoValido(value);
    }
}
