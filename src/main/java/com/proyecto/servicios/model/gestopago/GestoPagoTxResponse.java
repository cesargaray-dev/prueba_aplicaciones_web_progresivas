package com.proyecto.servicios.model.gestopago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Modelo para mapear la respuesta XML de GestoPago en el endpoint sendTx.do.
 * <p>
 * Ejemplo de respuesta exitosa:
 * <pre>
 * {@code
 * <RESPONSE>
 *     <ID_TX>291620941</ID_TX>
 *     <NUM_AUTORIZACION>100074211</NUM_AUTORIZACION>
 *     <SALDO>154997.91</SALDO>
 *     <COMISION>6.0</COMISION>
 *     <SALDO_F>154,997.91</SALDO_F>
 *     <COMISION_F>6</COMISION_F>
 *     <FECHA>25/Feb/2022 18:59:34</FECHA>
 *     <MONTO>937.99</MONTO>
 *     <MENSAJE>
 *         <CODIGO>01</CODIGO>
 *         <TEXTO>Operacion realizada con exito</TEXTO>
 *         <REFERENCIA>25720381</REFERENCIA>
 *     </MENSAJE>
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
public class GestoPagoTxResponse {

    @JacksonXmlProperty(localName = "ID_TX")
    private Long idTx;

    @JacksonXmlProperty(localName = "NUM_AUTORIZACION")
    private String numAutorizacion;

    @JacksonXmlProperty(localName = "SALDO")
    private BigDecimal saldo;

    @JacksonXmlProperty(localName = "COMISION")
    private BigDecimal comision;

    @JacksonXmlProperty(localName = "SALDO_F")
    private String saldoFormateado;

    @JacksonXmlProperty(localName = "COMISION_F")
    private String comisionFormateada;

    @JacksonXmlProperty(localName = "FECHA")
    private String fecha;

    @JacksonXmlProperty(localName = "MONTO")
    private BigDecimal monto;

    @JacksonXmlProperty(localName = "MENSAJE")
    private MensajeTx mensaje;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MensajeTx {

        @JacksonXmlProperty(localName = "CODIGO")
        private String codigo;

        @JacksonXmlProperty(localName = "TEXTO")
        private String texto;

        @JacksonXmlProperty(localName = "REFERENCIA")
        private String referencia;

        @JacksonXmlProperty(localName = "PIN")
        private String pin;

        @JacksonXmlProperty(localName = "legend")
        private String legend;
    }
}
