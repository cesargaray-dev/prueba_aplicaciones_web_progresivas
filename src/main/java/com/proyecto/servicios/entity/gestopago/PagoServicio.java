package com.proyecto.servicios.entity.gestopago;

import com.proyecto.servicios.entity.clientes.Cuenta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos_servicios")
@Getter
@Setter
public class PagoServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    @Column(name = "numero_cuenta", nullable = false, columnDefinition = "TEXT")
    private String numeroCuenta;

    @Column(name = "id_servicio", nullable = false)
    private Integer idServicio;

    @Column(name = "id_producto", nullable = false)
    private Integer idProducto;

    @Column(name = "nombre_servicio", columnDefinition = "TEXT")
    private String nombreServicio;

    @Column(name = "nombre_producto", columnDefinition = "TEXT")
    private String nombreProducto;

    @Column(name = "referencia", nullable = false, columnDefinition = "TEXT")
    private String referencia;

    @Column(name = "telefono", nullable = false, columnDefinition = "TEXT")
    private String telefono;

    @Column(name = "monto_pagado", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoPagado;

    @Column(name = "comision", nullable = false, precision = 18, scale = 2)
    private BigDecimal comision = BigDecimal.ZERO;

    @Column(name = "saldo_anterior", nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoAnterior;

    @Column(name = "saldo_restante", nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoRestante;

    @Column(name = "id_tx_gestopago", nullable = false)
    private Long idTxGestopago;

    @Column(name = "num_autorizacion", nullable = false, columnDefinition = "TEXT")
    private String numAutorizacion;

    @Column(name = "folio_ticket", nullable = false, unique = true, columnDefinition = "TEXT")
    private String folioTicket;

    @Column(name = "upc", nullable = false, columnDefinition = "TEXT")
    private String upc;

    @Column(name = "codigo_respuesta", nullable = false, columnDefinition = "TEXT")
    private String codigoRespuesta;

    @Column(name = "mensaje_respuesta", columnDefinition = "TEXT")
    private String mensajeRespuesta;

    @Column(name = "concepto", nullable = false, columnDefinition = "TEXT")
    private String concepto;

    @Column(name = "fecha_operacion", nullable = false, updatable = false)
    private LocalDateTime fechaOperacion;

    @PrePersist
    void onCreate() {
        if (fechaOperacion == null) {
            fechaOperacion = LocalDateTime.now();
        }
    }
}
