package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.clientes.CatalogoConsultaRequestDTO;
import com.proyecto.servicios.model.clientes.CatalogoResponseDTO;
import com.proyecto.servicios.model.clientes.CodigoPostalSepomexDTO;
import com.proyecto.servicios.repositorys.clientes.CatalogoRepository;
import com.proyecto.servicios.service.SepomexCatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/catalogos")
@Tag(name = "Catálogos", description = "Operaciones de consulta de catálogos del sistema y SEPOMEX")
public class CatalogoController {

    private final CatalogoRepository catalogoRepository;
    private final SepomexCatalogoService sepomexCatalogoService;

    public CatalogoController(CatalogoRepository catalogoRepository) {
        this(catalogoRepository, new SepomexCatalogoService());
    }

    @Autowired
    public CatalogoController(
            CatalogoRepository catalogoRepository,
            SepomexCatalogoService sepomexCatalogoService) {
        this.catalogoRepository = catalogoRepository;
        this.sepomexCatalogoService = sepomexCatalogoService != null
                ? sepomexCatalogoService
                : new SepomexCatalogoService();
    }

    private static final List<String> CATALOGO_OCUPACIONES_OFICIAL = List.of(
            "Empleado(a) del sector privado",
            "Servidor(a) público(a)",
            "Profesionista independiente",
            "Trabajador(a) por cuenta propia / Oficio",
            "Empresario(a) / Dueño(a) de negocio",
            "Comerciante",
            "Docente / Académico(a)",
            "Personal del sector salud",
            "Fuerzas armadas / Seguridad pública",
            "Agricultor(a) / Ganadero(a) / Pescador(a)",
            "Estudiante",
            "Labores del hogar",
            "Jubilado(a) / Pensionado(a)",
            "Desempleado(a) / En búsqueda de empleo",
            "Otra ocupación"
    );

    @GetMapping("/ocupaciones")
    @Operation(
            summary = "Consultar Catálogo Oficial de Ocupaciones",
            description = "Devuelve el catálogo oficial de ocupaciones y actividades económicas para apertura de cuenta KYC."
    )
    public ResponseEntity<List<String>> consultarOcupaciones() {
        return ResponseEntity.ok(CATALOGO_OCUPACIONES_OFICIAL);
    }

    @GetMapping("/cp/{codigoPostal}")
    @Operation(
            summary = "Consultar Estado, Municipio y Colonias exactas por Código Postal (SEPOMEX)",
            description = "Devuelve automáticamente el Estado, Municipio/Alcaldía y el catálogo exclusivo de colonias pertenecientes al código postal mexicano de 5 dígitos."
    )
    public ResponseEntity<CodigoPostalSepomexDTO> consultarCodigoPostal(
            @PathVariable("codigoPostal") String codigoPostal) {
        return ResponseEntity.ok(sepomexCatalogoService.consultarCodigoPostal(codigoPostal));
    }

    @PostMapping("/consultar")
    @Operation(summary = "Obtener catálogo por tipo", description = "Obtiene los elementos activos de un catálogo dado su tipo mediante JSON con @RequestBody (ej. NACIONALIDAD, SEXO, ESTADO_CIVIL, OCUPACION)")
    public ResponseEntity<List<CatalogoResponseDTO>> obtenerCatalogoPorTipo(@Valid @RequestBody CatalogoConsultaRequestDTO request) {
        List<CatalogoResponseDTO> catalogos = catalogoRepository.findByTipoAndActivoTrueOrderByOrdenAsc(request.getTipo().toUpperCase())
                .stream()
                .map(c -> {
                    CatalogoResponseDTO dto = new CatalogoResponseDTO();
                    dto.setId(c.getId());
                    dto.setTipo(c.getTipo());
                    dto.setClave(c.getClave());
                    dto.setDescripcion(c.getDescripcion());
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(catalogos);
    }
}
