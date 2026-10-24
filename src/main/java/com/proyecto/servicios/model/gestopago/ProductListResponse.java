package com.proyecto.servicios.model.gestopago;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.proyecto.servicios.enums.DataSourceEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.List;

/**
 * Mapeo de la respuesta XML del servicio externo GestoPago.
 * <p>
 * Estructura real de la API:
 * <pre>
 * {@code
 * <RESPONSE>
 *   <MENSAJE>...</MENSAJE>
 *   <PRODUCTOS>
 *     <producto servicio="..." producto="..." idProducto="..." precio="..." tipoFront="..." .../>
 *   </PRODUCTOS>
 * </RESPONSE>
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "RESPONSE")
public class ProductListResponse {

    /** Mensaje de respuesta del servidor (elemento XML: MENSAJE) */
    @JacksonXmlProperty(localName = "MENSAJE")
    @JsonAlias({"message", "MENSAJE"})
    @JsonDeserialize(using = MensajeFlexibleDeserializer.class)
    private String message;

    private Integer codigoCache;
    private String origenCache;

    /** Lista de productos (elemento contenedor: PRODUCTOS, elementos hijos: producto) */
    @JacksonXmlElementWrapper(localName = "PRODUCTOS")
    @JacksonXmlProperty(localName = "producto")
    @JsonAlias({"data", "PRODUCTOS"})
    private List<ProductDTO> data;

    /**
     * Campo de estado calculado: "OK" si la respuesta tiene productos,
     * "VACIO" si la lista está vacía, "ERROR" si hubo fallo.
     */
    private String status;

    /**
     * Calcula el estado basado en los datos recibidos del XML.
     * Se invoca manualmente después del parseo.
     */
    public void calcularStatus() {
        if (data != null && !data.isEmpty()) {
            this.status = "OK";
        } else if (message != null && !message.isEmpty()) {
            this.status = "VACIO";
        } else {
            this.message = "Operación exitosa";
        }
    }

    public void setOrigenDatos(DataSourceEnum origen) {
        if (origen != null) {
            this.codigoCache = origen.getCodigo();
            this.origenCache = origen.getDescripcion();
        }
    }

    public static class MensajeFlexibleDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                return node.asText();
            }
            if (node.isObject() && node.has("TEXTO")) {
                return node.get("TEXTO").asText();
            }
            return node.asText();
        }
    }
}
