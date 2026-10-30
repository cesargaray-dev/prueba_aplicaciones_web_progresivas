package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.entity.gestopago.PagoServicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PagoServicioRepository extends JpaRepository<PagoServicio, Long> {

    List<PagoServicio> findByNumeroCuentaOrderByFechaOperacionDesc(String numeroCuenta);

    Optional<PagoServicio> findByFolioTicket(String folioTicket);

    boolean existsByFolioTicket(String folioTicket);

    /**
     * Verifica si ya existe una operación idéntica reciente para prevenir cargos duplicados
     * en una ventana de idempotencia.
     */
    boolean existsByNumeroCuentaAndIdServicioAndIdProductoAndReferenciaAndTelefonoAndMontoPagadoAndFechaOperacionAfter(
            String numeroCuenta,
            Integer idServicio,
            Integer idProducto,
            String referencia,
            String telefono,
            BigDecimal montoPagado,
            LocalDateTime fechaLimite
    );
}
