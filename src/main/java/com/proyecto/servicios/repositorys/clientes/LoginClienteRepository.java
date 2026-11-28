package com.proyecto.servicios.repositorys.clientes;

import com.proyecto.servicios.entity.clientes.LoginCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginClienteRepository extends JpaRepository<LoginCliente, Long> {

    Optional<LoginCliente> findByUsuario(String usuario);

    @org.springframework.data.jpa.repository.Query("SELECT l FROM LoginCliente l WHERE LOWER(TRIM(l.usuario)) = LOWER(TRIM(:usuario))")
    Optional<LoginCliente> findByUsuarioIgnoreCase(@org.springframework.data.repository.query.Param("usuario") String usuario);

    Optional<LoginCliente> findByClienteId(Long clienteId);

    Optional<LoginCliente> findByTokenSesionActiva(String tokenSesionActiva);

    Optional<LoginCliente> findByTokenSesionDesplazada(String tokenSesionDesplazada);
}
