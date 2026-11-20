package com.proyecto.servicios.model.gestopago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa un producto individual de GestoPago.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDTO {

    private static final String CDN_LOGO_PREFIX =
            "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/";

    @JacksonXmlProperty(isAttribute = true, localName = "idProducto")
    private String id;

    /** Nombre del producto */
    @JacksonXmlProperty(isAttribute = true, localName = "producto")
    private String name;

    /** Nombre del servicio al que pertenece el producto */
    @JacksonXmlProperty(isAttribute = true, localName = "servicio")
    private String service;

    /** ID del servicio */
    @JacksonXmlProperty(isAttribute = true, localName = "idServicio")
    private String serviceId;

    /** Precio del producto */
    @JacksonXmlProperty(isAttribute = true, localName = "precio")
    private String price;

    /** Tipo de referencia para el pago */
    @JacksonXmlProperty(isAttribute = true, localName = "tipoReferencia")
    private String referenceType;

    /** Tipo de front para clasificación de la interfaz */
    @JacksonXmlProperty(isAttribute = true, localName = "tipoFront")
    private String frontType;

    /** Indica si tiene dígito verificador */
    @JacksonXmlProperty(isAttribute = true, localName = "hasDigitoVerificador")
    private String hasVerificationDigit;

    /** ID de la categoría del tipo de servicio */
    @JacksonXmlProperty(isAttribute = true, localName = "idCatTipoServicio")
    private String categoryId;

    /** Leyenda o descripción informativa del producto cuando está disponible */
    @JacksonXmlProperty(isAttribute = true, localName = "legend")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String legend;

    /** Indica si el proveedor ofrece ayuda visual para localizar la referencia */
    @JacksonXmlProperty(isAttribute = true, localName = "showAyuda")
    private Boolean showAyuda;

    /** URL oficial del logotipo del proveedor en el CDN de GestoPago */
    private String logoUrl;

    public ProductDTO(
            String id,
            String name,
            String service,
            String serviceId,
            String price,
            String referenceType,
            String frontType,
            String hasVerificationDigit,
            String categoryId) {
        this.id = id;
        this.name = name;
        this.service = service;
        this.serviceId = serviceId;
        this.price = price;
        this.referenceType = referenceType;
        this.frontType = frontType;
        this.hasVerificationDigit = hasVerificationDigit;
        this.categoryId = categoryId;
    }

    public Boolean getShowAyuda() {
        return showAyuda != null ? showAyuda : Boolean.FALSE;
    }

    public void setShowAyuda(Object rawShowAyuda) {
        if (rawShowAyuda == null) {
            this.showAyuda = Boolean.FALSE;
        } else if (rawShowAyuda instanceof Boolean b) {
            this.showAyuda = b;
        } else {
            this.showAyuda = "true".equalsIgnoreCase(rawShowAyuda.toString().trim())
                    || "1".equals(rawShowAyuda.toString().trim());
        }
    }

    public String getLogoUrl() {
        if (logoUrl != null && !logoUrl.isBlank()) {
            return logoUrl;
        }
        if (serviceId != null && !serviceId.isBlank()) {
            return CDN_LOGO_PREFIX + serviceId.trim() + ".png";
        }
        return null;
    }
}
