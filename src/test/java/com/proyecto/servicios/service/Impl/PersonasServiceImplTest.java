package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.Personas;
import com.proyecto.servicios.model.EliminaPersonaRequest;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.PersonaResponse;
import com.proyecto.servicios.model.PersonasRequest;
import com.proyecto.servicios.repositorys.sf.PersonasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonasServiceImplTest {

    @Mock
    private PersonasRepository personasRepository;

    private PersonasServiceImpl personasService;

    @BeforeEach
    void setUp() {
        personasService = new PersonasServiceImpl();
        ReflectionTestUtils.setField(personasService, "personasRepository", personasRepository);
    }

    @Test
    void creaPersona_guardaYRetornaPersonaResponse() {
        PersonasRequest request = new PersonasRequest();
        request.setNombre("Juan");
        request.setApellidoP("Perez");
        request.setApellidoMaterno("Lopez");

        when(personasRepository.save(any(Personas.class))).thenAnswer(i -> i.getArgument(0));

        PersonaResponse response = personasService.creaPersona(request);

        assertNotNull(response);
        assertEquals(1, response.getCodigo());
        assertEquals("Exito", response.getMensaje());
        verify(personasRepository, times(1)).save(any(Personas.class));
    }

    @Test
    void eliminaPersona_cuandoExiste_eliminaYRetornaCodigo0() {
        EliminaPersonaRequest request = new EliminaPersonaRequest();
        request.setNombre("Juan");

        Personas persona = new Personas();
        persona.setNombre("Juan");

        when(personasRepository.findByNombre("Juan")).thenReturn(Optional.of(persona));

        GenericResponse response = personasService.eliminaPersona(request);

        assertNotNull(response);
        assertEquals(0, response.getCodigo());
        assertTrue(response.getMensaje().contains("correctamente"));
        verify(personasRepository, times(1)).delete(persona);
    }

    @Test
    void eliminaPersona_cuandoNoExiste_retornaCodigo1() {
        EliminaPersonaRequest request = new EliminaPersonaRequest();
        request.setNombre("Inexistente");

        when(personasRepository.findByNombre("Inexistente")).thenReturn(Optional.empty());

        GenericResponse response = personasService.eliminaPersona(request);

        assertNotNull(response);
        assertEquals(1, response.getCodigo());
        verify(personasRepository, never()).delete(any());
    }

    @Test
    void actualizaPersona_cuandoExiste_actualizaYRetornaCodigo0() {
        PersonasRequest request = new PersonasRequest();
        request.setNombre("Juan");
        request.setApellidoP("NuevoP");
        request.setApellidoMaterno("NuevoM");

        Personas persona = new Personas();
        persona.setNombre("Juan");

        when(personasRepository.findByNombre("Juan")).thenReturn(Optional.of(persona));

        GenericResponse response = personasService.actualizaPersona(request);

        assertNotNull(response);
        assertEquals(0, response.getCodigo());
        assertEquals("NuevoP", persona.getApellidoP());
        assertEquals("NuevoM", persona.getApellidoMaterno());
        verify(personasRepository, times(1)).save(persona);
    }

    @Test
    void actualizaPersona_cuandoNoExiste_retornaCodigo1() {
        PersonasRequest request = new PersonasRequest();
        request.setNombre("Inexistente");

        when(personasRepository.findByNombre("Inexistente")).thenReturn(Optional.empty());

        GenericResponse response = personasService.actualizaPersona(request);

        assertNotNull(response);
        assertEquals(1, response.getCodigo());
        verify(personasRepository, never()).save(any());
    }
}

