package com.proyecto.servicios.repositorys.clientes;

import com.proyecto.servicios.entity.clientes.DatoBiometrico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatoBiometricoRepository extends JpaRepository<DatoBiometrico, Long> {

    Optional<DatoBiometrico> findByClienteIdAndTipo(Long clienteId, String tipo);

    List<DatoBiometrico> findByClienteId(Long clienteId);
}
