package com.proyecto.servicios.service;

import com.proyecto.servicios.model.clientes.CodigoPostalSepomexDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SepomexCatalogoServiceTest {

    @Test
    void consultarCodigoPostal_resuelveEstadoMunicipioYColoniasExactasDeSepomex() {
        SepomexCatalogoService service = new SepomexCatalogoService();
        service.init();

        CodigoPostalSepomexDTO cdmx = service.consultarCodigoPostal("01030");
        assertTrue(cdmx.isValido());
        assertEquals("Ciudad de México", cdmx.getEstado());
        assertEquals("DF", cdmx.getCodigoEstado());
        assertEquals("Álvaro Obregón", cdmx.getMunicipio());
        assertTrue(cdmx.getColonias().contains("Florida"));
        assertTrue(cdmx.getColonias().contains("Axotla"));
        assertTrue(service.perteneceColoniaACodigoPostal("01030", "Florida"));
        assertFalse(service.perteneceColoniaACodigoPostal("01030", "Polanco"));

        CodigoPostalSepomexDTO dolores = service.consultarCodigoPostal("37800");
        assertTrue(dolores.isValido());
        assertEquals("Guanajuato", dolores.getEstado());
        assertTrue(dolores.getMunicipio().contains("Dolores Hidalgo"));
        assertFalse(dolores.getColonias().isEmpty());

        assertFalse(service.consultarCodigoPostal("123").isValido());
        assertFalse(service.consultarCodigoPostal("18500").isValido());
        assertFalse(service.perteneceColoniaACodigoPostal("18500", "Centro"));
        assertFalse(service.perteneceColoniaACodigoPostal("06600", ""));
    }
}
