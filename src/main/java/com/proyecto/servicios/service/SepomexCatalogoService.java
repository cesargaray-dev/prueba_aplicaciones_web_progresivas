package com.proyecto.servicios.service;

import com.proyecto.servicios.model.clientes.CodigoPostalSepomexDTO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Catálogo oficial SEPOMEX en memoria cargado desde sepomex/sepomex_cp.tsv.gz (31,868 C.P. de México).
 * Permite resolver automáticamente Estado, Municipio/Alcaldía y el listado exacto de Colonias
 * pertenecientes exclusivamente al código postal consultado.
 */
@Service
@Slf4j
public class SepomexCatalogoService {

    private static final String RESOURCE_PATH = "sepomex/sepomex_cp.tsv.gz";
    private Map<String, EntradaCp> indicePorCp = Collections.emptyMap();

    @PostConstruct
    public void init() {
        cargarCatalogo();
    }

    public synchronized void cargarCatalogo() {
        if (!indicePorCp.isEmpty()) {
            return;
        }
        Map<String, EntradaCp> temp = new HashMap<>(40_000);
        try {
            ClassPathResource res = new ClassPathResource(RESOURCE_PATH);
            if (!res.exists()) {
                log.warn("No se encontró el recurso SEPOMEX en classpath: {}", RESOURCE_PATH);
                return;
            }
            try (GZIPInputStream gis = new GZIPInputStream(res.getInputStream());
                 BufferedReader br = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\t", 5);
                    if (parts.length < 5) continue;
                    String cp = parts[0].trim();
                    String codEstado = parts[1].trim();
                    String estado = parts[2].trim();
                    String municipio = parts[3].trim();
                    List<String> colonias = Arrays.stream(parts[4].split("\\|"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();
                    if (!cp.isEmpty() && !colonias.isEmpty()) {
                        temp.put(cp, new EntradaCp(codEstado, estado, municipio, colonias));
                    }
                }
            }
            indicePorCp = Collections.unmodifiableMap(temp);
            log.info("Catálogo oficial SEPOMEX cargado exitosamente: {} códigos postales únicos", indicePorCp.size());
        } catch (Exception e) {
            log.error("Error al cargar catálogo SEPOMEX comprimido: {}", e.getMessage(), e);
        }
    }

    public CodigoPostalSepomexDTO consultarCodigoPostal(String rawCp) {
        cargarCatalogo();
        String cp = rawCp != null ? rawCp.trim() : "";
        if (!cp.matches("^\\d{5}$")) {
            return CodigoPostalSepomexDTO.builder()
                    .codigoPostal(cp)
                    .valido(false)
                    .colonias(List.of())
                    .mensaje("El código postal debe contener exactamente 5 dígitos numéricos.")
                    .build();
        }

        EntradaCp entrada = indicePorCp.get(cp);
        if (entrada == null) {
            return CodigoPostalSepomexDTO.builder()
                    .codigoPostal(cp)
                    .valido(false)
                    .colonias(List.of())
                    .mensaje("El código postal " + cp + " no existe en el catálogo oficial SEPOMEX de México.")
                    .build();
        }

        return CodigoPostalSepomexDTO.builder()
                .codigoPostal(cp)
                .valido(true)
                .pais("México")
                .codigoEstado(entrada.codigoEstado())
                .estado(entrada.estado())
                .municipio(entrada.municipio())
                .colonias(entrada.colonias())
                .mensaje("Código postal verificado en SEPOMEX.")
                .build();
    }

    public boolean perteneceColoniaACodigoPostal(String cp, String colonia) {
        if (colonia == null || colonia.isBlank()) {
            return false;
        }
        CodigoPostalSepomexDTO info = consultarCodigoPostal(cp);
        if (!info.isValido() || info.getColonias() == null || info.getColonias().isEmpty()) {
            return false;
        }
        String buscadaNorm = normalizarTexto(colonia);
        return info.getColonias().stream()
                .anyMatch(c -> normalizarTexto(c).equals(buscadaNorm));
    }

    private static String normalizarTexto(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private record EntradaCp(String codigoEstado, String estado, String municipio, List<String> colonias) {
    }
}
