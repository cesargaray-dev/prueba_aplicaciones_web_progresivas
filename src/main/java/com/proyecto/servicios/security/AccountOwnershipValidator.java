package com.proyecto.servicios.security;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.clientes.LoginCliente;
import com.proyecto.servicios.exception.AutenticacionException;
import com.proyecto.servicios.repositorys.clientes.CuentaRepository;
import com.proyecto.servicios.repositorys.clientes.LoginClienteRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AccountOwnershipValidator {

    private static final String MENSAJE_FORBIDDEN = "No tienes permisos para operar sobre la cuenta indicada.";

    private final LoginClienteRepository loginClienteRepository;
    private final CuentaRepository cuentaRepository;

    public AccountOwnershipValidator(
            LoginClienteRepository loginClienteRepository,
            CuentaRepository cuentaRepository) {
        this.loginClienteRepository = loginClienteRepository;
        this.cuentaRepository = cuentaRepository;
    }

    public void validarPropiedadCuenta(String usuarioAutenticado, String numeroCuenta) {
        if (usuarioAutenticado == null || usuarioAutenticado.isBlank()) {
            throw new AutenticacionException("Sesión no autorizada.");
        }
        if (numeroCuenta == null || numeroCuenta.isBlank()) {
            throw new AccessDeniedException(MENSAJE_FORBIDDEN);
        }

        LoginCliente login = loginClienteRepository.findByUsuario(usuarioAutenticado.trim())
                .orElseThrow(() -> new AutenticacionException("Sesión no autorizada."));

        Cliente cliente = login.getCliente();
        if (cliente == null || cliente.getId() == null) {
            throw new AccessDeniedException(MENSAJE_FORBIDDEN);
        }

        Optional<Cuenta> cuentaOpt = cuentaRepository.findByClienteId(cliente.getId());
        Cuenta cuenta = cuentaOpt.orElse(cliente.getCuenta());
        if (cuenta == null || cuenta.getNumeroCuenta() == null
                || !cuenta.getNumeroCuenta().equals(numeroCuenta.trim())) {
            throw new AccessDeniedException(MENSAJE_FORBIDDEN);
        }
    }
}
