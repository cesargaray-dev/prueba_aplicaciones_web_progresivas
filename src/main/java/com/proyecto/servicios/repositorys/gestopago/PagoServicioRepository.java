package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.entity.gestopago.PagoServicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagoServicioRepository extends JpaRepository<PagoServicio, Long> {

    List<PagoServicio> findByNumeroCuentaOrderByFechaOperacionDesc(String numeroCuenta);

    Optional<PagoServicio> findByFolioTicket(String folioTicket);

    boolean existsByFolioTicket(String folioTicket);
}
