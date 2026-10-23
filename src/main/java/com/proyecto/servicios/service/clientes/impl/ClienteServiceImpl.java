package com.proyecto.servicios.service.clientes.impl;

import com.proyecto.servicios.entity.clientes.*;
import com.proyecto.servicios.enums.EstatusClienteEnum;
import com.proyecto.servicios.enums.EstatusCuentaEnum;
import com.proyecto.servicios.exception.*;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.clientes.ClienteBusquedaRequestDTO;
import com.proyecto.servicios.model.clientes.ClienteIdRequestDTO;
import com.proyecto.servicios.model.clientes.ClienteRequestDTO;
import com.proyecto.servicios.model.clientes.ClienteResponseDTO;
import com.proyecto.servicios.model.clientes.CuentaResponseDTO;
import com.proyecto.servicios.repositorys.clientes.*;
import com.proyecto.servicios.repositorys.clientes.specification.ClienteSpecification;
import com.proyecto.servicios.service.clientes.ClienteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;
    private final CuentaRepository cuentaRepository;
    private final LoginClienteRepository loginRepository;
    private final DatoBiometricoRepository biometricoRepository;
    private final CatalogoRepository catalogoRepository;

    @Override
    @Transactional
    public ClienteResponseDTO registrarCliente(ClienteRequestDTO request) {
        log.info("Iniciando registro de cliente con CURP: {}", request.getCurp());
        
        validarReglasNegocio(request);

        // 1. Crear Cliente
        Cliente cliente = new Cliente();
        mapearDatosPersonales(request, cliente);
        cliente = clienteRepository.save(cliente);

        // 2. Crear Domicilio
        Domicilio domicilio = new Domicilio();
        mapearDomicilio(request, domicilio);
        domicilio.setCliente(cliente);
        cliente.setDomicilio(domicilio);

        // 3. Crear Cuenta Bancaria
        Cuenta cuenta = crearCuentaBancaria(cliente);
        cliente.setCuenta(cuenta);

        // 4. Crear Login (Opcional si vienen datos)
        if (request.getUsuario() != null && !request.getUsuario().isBlank() && 
            request.getContrasena() != null && !request.getContrasena().isBlank()) {
            LoginCliente login = crearLogin(cliente, request.getUsuario(), request.getContrasena());
            cliente.setLoginCliente(login);
        }

        // 5. Crear Datos Biométricos (Ej: MediaPipe Embedding Hash)
        if (request.getHashBiometrico() != null && !request.getHashBiometrico().isBlank()) {
            crearDatoBiometrico(cliente, request.getHashBiometrico());
        }

        clienteRepository.save(cliente);
        log.info("Cliente registrado exitosamente con ID: {}", cliente.getId());
        
        return mapToResponse(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> obtenerTodosLosClientes() {
        return clienteRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponseDTO obtenerClientePorId(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con ID: " + id));
        return mapToResponse(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponseDTO obtenerClientePorCurp(String curp) {
        Cliente cliente = clienteRepository.findByCurp(curp)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con CURP: " + curp));
        return mapToResponse(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponseDTO obtenerClientePorRfc(String rfc) {
        Cliente cliente = clienteRepository.findByRfc(rfc)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con RFC: " + rfc));
        return mapToResponse(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponseDTO obtenerClientePorCorreo(String correo) {
        Cliente cliente = clienteRepository.findByCorreo(correo)
                .orElseThrow(() -> new ClienteNoEncontradoException("El correo electrónico '" + correo + "' no está registrado en el sistema"));
        return mapToResponse(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponseDTO obtenerClientePorNumeroCuenta(String numeroCuenta) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException("Cuenta no encontrada: " + numeroCuenta));
        return mapToResponse(cuenta.getCliente());
    }

    @Override
    @Transactional
    public ClienteResponseDTO actualizarCliente(String curp, ClienteRequestDTO request) {
        Cliente cliente = clienteRepository.findByCurp(curp)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con CURP: " + curp));

        // Actualizar datos permitidos (NO CURP, NO RFC)
        cliente.setNombre(request.getNombre());
        cliente.setSegundoNombre(request.getSegundoNombre());
        cliente.setApellidoPaterno(request.getApellidoPaterno());
        cliente.setApellidoMaterno(request.getApellidoMaterno());
        cliente.setFechaNacimiento(parseFecha(request.getFechaNacimiento()));
        cliente.setSexo(request.getSexo());
        // Resolver descripción de nacionalidad desde el catálogo
        String descNacionalidad = catalogoRepository.findById(request.getNacionalidadId())
                .filter(c -> "NACIONALIDAD".equals(c.getTipo()) && Boolean.TRUE.equals(c.getActivo()))
                .map(cat -> cat.getDescripcion())
                .orElseThrow(() -> new ValidacionException(
                        "La nacionalidad con ID " + request.getNacionalidadId() + " no existe en el catálogo"));
        cliente.setNacionalidad(descNacionalidad);
        cliente.setEstadoCivil(request.getEstadoCivil());
        cliente.setOcupacion(request.getOcupacion());
        cliente.setEmpresa(request.getEmpresa());
        cliente.setIngresoMensual(request.getIngresoMensual());
        
        // Correo (validar unicidad si cambia)
        if (!cliente.getCorreo().equals(request.getCorreo())) {
            if (clienteRepository.existsByCorreo(request.getCorreo())) {
                throw new CorreoDuplicadoException("El correo ya está registrado en otra cuenta");
            }
            cliente.setCorreo(request.getCorreo());
        }
        
        cliente.setTelefonoMovil(request.getTelefonoMovil());
        cliente.setTelefonoAlternativo(request.getTelefonoAlternativo());

        // Actualizar Domicilio
        if (request.getEstado() != null) {
            boolean estadoValido = catalogoRepository.existsByTipoAndDescripcionIgnoreCaseAndActivoTrue("ESTADO", request.getEstado().trim())
                    || catalogoRepository.existsByTipoAndClaveIgnoreCaseAndActivoTrue("ESTADO", request.getEstado().trim());
            if (!estadoValido) {
                throw new ValidacionException("El estado '" + request.getEstado() + "' no es válido o no existe en el catálogo oficial de entidades federativas.");
            }
        }
        if (cliente.getDomicilio() != null) {
            mapearDomicilio(request, cliente.getDomicilio());
        }

        cliente = clienteRepository.save(cliente);
        ClienteResponseDTO response = mapToResponse(cliente);
        response.setCodigo(0);
        response.setMensaje("Datos actualizados correctamente");
        return response;
    }

    @Override
    @Transactional
    public ClienteResponseDTO actualizarCompleto(ClienteRequestDTO request) {
        Cliente cliente;
        if (request.getId() != null) {
            cliente = clienteRepository.findById(request.getId())
                    .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con ID: " + request.getId()));
        } else if (request.getCurp() != null && !request.getCurp().isBlank()) {
            cliente = clienteRepository.findByCurp(request.getCurp())
                    .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con CURP: " + request.getCurp()));
        } else if (request.getRfc() != null && !request.getRfc().isBlank()) {
            cliente = clienteRepository.findByRfc(request.getRfc())
                    .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con RFC: " + request.getRfc()));
        } else {
            throw new ValidacionException("Debe proporcionar el ID, CURP o RFC del cliente para actualizar");
        }
        return actualizarCliente(cliente.getCurp(), request);
    }

    @Override
    @Transactional
    public ClienteResponseDTO actualizarParcial(com.proyecto.servicios.model.clientes.ClienteActualizacionParcialRequestDTO request) {
        Cliente cliente;
        if (request.getId() != null) {
            cliente = clienteRepository.findById(request.getId())
                    .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con ID: " + request.getId()));
        } else if (request.getCurp() != null && !request.getCurp().isBlank()) {
            cliente = clienteRepository.findByCurp(request.getCurp())
                    .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con CURP: " + request.getCurp()));
        } else {
            throw new ValidacionException("Debe proporcionar el ID o la CURP del cliente para actualizar");
        }

        // Datos personales
        if (request.getNombre() != null) cliente.setNombre(request.getNombre());
        if (request.getSegundoNombre() != null) cliente.setSegundoNombre(request.getSegundoNombre());
        if (request.getApellidoPaterno() != null) cliente.setApellidoPaterno(request.getApellidoPaterno());
        if (request.getApellidoMaterno() != null) cliente.setApellidoMaterno(request.getApellidoMaterno());
        if (request.getFechaNacimiento() != null) {
            LocalDate fechaNac = parseFecha(request.getFechaNacimiento());
            if (fechaNac.isAfter(LocalDate.now())) {
                throw new ValidacionException("La fecha de nacimiento no puede ser una fecha futura");
            }
            if (Period.between(fechaNac, LocalDate.now()).getYears() < 18) {
                throw new ValidacionException("El cliente debe ser mayor de edad (18 años o más)");
            }
            cliente.setFechaNacimiento(fechaNac);
        }
        if (request.getSexo() != null) cliente.setSexo(request.getSexo());
        if (request.getNacionalidadId() != null) {
            String descNacionalidad = catalogoRepository.findById(request.getNacionalidadId())
                    .filter(c -> "NACIONALIDAD".equals(c.getTipo()) && Boolean.TRUE.equals(c.getActivo()))
                    .map(cat -> cat.getDescripcion())
                    .orElseThrow(() -> new ValidacionException(
                            "La nacionalidad con ID " + request.getNacionalidadId() + " no existe en el catálogo"));
            cliente.setNacionalidad(descNacionalidad);
        }
        if (request.getEstadoCivil() != null) cliente.setEstadoCivil(request.getEstadoCivil());

        // Contacto
        if (request.getCorreo() != null && !request.getCorreo().equals(cliente.getCorreo())) {
            if (clienteRepository.existsByCorreo(request.getCorreo())) {
                throw new CorreoDuplicadoException("El correo ya está registrado en otra cuenta");
            }
            cliente.setCorreo(request.getCorreo());
        }
        if (request.getTelefonoMovil() != null) cliente.setTelefonoMovil(request.getTelefonoMovil());
        if (request.getTelefonoAlternativo() != null) cliente.setTelefonoAlternativo(request.getTelefonoAlternativo());

        // Laborales
        if (request.getOcupacion() != null) cliente.setOcupacion(request.getOcupacion());
        if (request.getEmpresa() != null) cliente.setEmpresa(request.getEmpresa());
        if (request.getIngresoMensual() != null) cliente.setIngresoMensual(request.getIngresoMensual());

        // Domicilio
        Domicilio d = cliente.getDomicilio();
        if (d != null) {
            if (request.getEstado() != null) {
                boolean estadoValido = catalogoRepository.existsByTipoAndDescripcionIgnoreCaseAndActivoTrue("ESTADO", request.getEstado().trim())
                        || catalogoRepository.existsByTipoAndClaveIgnoreCaseAndActivoTrue("ESTADO", request.getEstado().trim());
                if (!estadoValido) {
                    throw new ValidacionException("El estado '" + request.getEstado() + "' no es válido o no existe en el catálogo oficial de entidades federativas.");
                }
                d.setEstado(request.getEstado());
            }
            if (request.getCalle() != null) d.setCalle(request.getCalle());
            if (request.getNumeroExterior() != null) d.setNumeroExterior(request.getNumeroExterior());
            if (request.getNumeroInterior() != null) d.setNumeroInterior(request.getNumeroInterior());
            if (request.getColonia() != null) d.setColonia(request.getColonia());
            if (request.getMunicipio() != null) d.setMunicipio(request.getMunicipio());
            if (request.getCodigoPostal() != null) d.setCodigoPostal(request.getCodigoPostal());
            if (request.getPais() != null) d.setPais(request.getPais());
        }

        cliente = clienteRepository.save(cliente);
        log.info("Cliente {} actualizado parcialmente", cliente.getCurp());
        ClienteResponseDTO response = mapToResponse(cliente);
        response.setCodigo(0);
        response.setMensaje("Datos actualizados parcialmente de forma correcta");
        return response;
    }

    @Override
    @Transactional
    public com.proyecto.servicios.model.clientes.ClienteResponse actualizarCompleto(com.proyecto.servicios.model.clientes.ClienteRegistroRequest request) {
        ClienteResponseDTO dto = actualizarCompleto((ClienteRequestDTO) request);
        com.proyecto.servicios.model.clientes.ClienteResponse response = new com.proyecto.servicios.model.clientes.ClienteResponse();
        org.springframework.beans.BeanUtils.copyProperties(dto, response);
        return response;
    }

    @Override
    @Transactional
    public void desactivarCliente(String curp) {
        Cliente cliente = clienteRepository.findByCurp(curp)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con CURP: " + curp));
        
        cliente.setActivo(false);
        
        // Si se desactiva el cliente, desactivamos la cuenta también
        if (cliente.getCuenta() != null) {
            cliente.getCuenta().setEstatus(EstatusCuentaEnum.INACTIVA.getCodigo());
        }
        if (cliente.getLoginCliente() != null) {
            cliente.getLoginCliente().setActivo(false);
        }
        
        clienteRepository.save(cliente);
        log.info("Cliente {} desactivado correctamente", curp);
    }

    @Override
    @Transactional
    public GenericResponse desactivarCliente(ClienteIdRequestDTO request) {
        if (request.getId() != null) {
            Cliente cliente = clienteRepository.findById(request.getId())
                    .orElseThrow(() -> new ClienteNoEncontradoException("No existe el cliente con ID: " + request.getId()));
            desactivarCliente(cliente.getCurp());
        } else if (request.getCurp() != null && !request.getCurp().isBlank()) {
            desactivarCliente(request.getCurp());
        } else {
            throw new ValidacionException("Debe proporcionar el ID o la CURP del cliente a desactivar");
        }
        return new GenericResponse(0, "Cliente desactivado exitosamente");
    }

    @Override
    @Transactional
    public ClienteResponseDTO reactivarCliente(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNoEncontradoException("No existe el cliente con ID: " + id));

        if (Boolean.TRUE.equals(cliente.getActivo())) {
            throw new ValidacionException("El cliente con ID " + id + " ya se encuentra activo");
        }

        cliente.setActivo(true);
        if (cliente.getCuenta() != null) {
            cliente.getCuenta().setEstatus(EstatusCuentaEnum.ACTIVA.getCodigo());
        }
        if (cliente.getLoginCliente() != null) {
            cliente.getLoginCliente().setActivo(true);
        }

        cliente = clienteRepository.save(cliente);
        log.info("Cliente con ID {} reactivado exitosamente", id);
        ClienteResponseDTO response = mapToResponse(cliente);
        response.setCodigo(0);
        response.setMensaje("Cliente reactivado correctamente");
        return response;
    }

    @Override
    @Transactional
    public ClienteResponseDTO reactivarCliente(ClienteIdRequestDTO request) {
        if (request.getId() != null) {
            return reactivarCliente(request.getId());
        }
        if (request.getCurp() != null && !request.getCurp().isBlank()) {
            Cliente cliente = clienteRepository.findByCurp(request.getCurp())
                    .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con CURP: " + request.getCurp()));
            return reactivarCliente(cliente.getId());
        }
        throw new ValidacionException("Debe proporcionar el ID o la CURP del cliente a reactivar");
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponseDTO buscarClienteDinamico(ClienteBusquedaRequestDTO peticion) {
        List<ClienteResponseDTO> resultados = buscarClientesDinamico(peticion);
        return resultados.get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> buscarClientesDinamico(ClienteBusquedaRequestDTO peticion) {
        boolean tieneCriterio = peticion.getId() != null
                || (peticion.getCurp() != null && !peticion.getCurp().isBlank())
                || (peticion.getRfc() != null && !peticion.getRfc().isBlank())
                || (peticion.getCorreo() != null && !peticion.getCorreo().isBlank())
                || (peticion.getNumeroCuenta() != null && !peticion.getNumeroCuenta().isBlank())
                || (peticion.getNombre() != null && !peticion.getNombre().isBlank())
                || (peticion.getApellidoPaterno() != null && !peticion.getApellidoPaterno().isBlank())
                || peticion.getActivo() != null;

        if (!tieneCriterio) {
            throw new ValidacionException("Debe proporcionar al menos un criterio de búsqueda (curp, rfc, correo, numeroCuenta, nombre, etc.)");
        }

        List<Cliente> clientes = clienteRepository.findAll(ClienteSpecification.conFiltros(peticion));

        if (clientes.isEmpty()) {
            throw new ClienteNoEncontradoException("No se encontraron clientes con los criterios de búsqueda proporcionados");
        }

        return clientes.stream()
                .map(c -> {
                    ClienteResponseDTO dto = mapToResponse(c);
                    dto.setCodigo(0);
                    dto.setMensaje("Consulta exitosa");
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> obtenerClientesActivos() {
        return clienteRepository.findByActivoTrueOrderByFechaRegistroAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> obtenerClientesPorRangoFechas(LocalDate inicio, LocalDate fin) {
        if (inicio.isAfter(fin)) {
            throw new ValidacionException("La fecha de inicio no puede ser posterior a la fecha de fin");
        }
        // Convertir LocalDate a LocalDateTime: inicio = 00:00:00, fin = 23:59:59
        LocalDateTime inicioDT = inicio.atStartOfDay();
        LocalDateTime finDT = fin.atTime(23, 59, 59);
        return clienteRepository.findByFechaRegistroBetweenOrderByFechaRegistroAsc(inicioDT, finDT).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CuentaResponseDTO obtenerCuentaPorNumero(String numeroCuenta) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException("Cuenta no encontrada: " + numeroCuenta));
        return mapToCuentaResponse(cuenta);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuentaResponseDTO> obtenerCuentasActivas() {
        return cuentaRepository.findByEstatusOrderByFechaAperturaAsc(EstatusCuentaEnum.ACTIVA.getCodigo()).stream()
                .map(this::mapToCuentaResponse)
                .collect(Collectors.toList());
    }

    // --- Métodos Privados Auxiliares ---

    private void validarReglasNegocio(ClienteRequestDTO request) {
        LocalDate fechaNac = parseFecha(request.getFechaNacimiento());
        if (fechaNac.isAfter(LocalDate.now())) {
            throw new ValidacionException("La fecha de nacimiento no puede ser una fecha futura");
        }
        // Validar mayoría de edad
        if (Period.between(fechaNac, LocalDate.now()).getYears() < 18) {
            throw new ValidacionException("El cliente debe ser mayor de edad (18 años o más)");
        }
        // Validar que el ID de nacionalidad exista en el catálogo
        catalogoRepository.findById(request.getNacionalidadId())
                .filter(c -> "NACIONALIDAD".equals(c.getTipo()) && Boolean.TRUE.equals(c.getActivo()))
                .orElseThrow(() -> new ValidacionException(
                        "La nacionalidad con ID " + request.getNacionalidadId() +
                        " no existe en el catálogo. Consulta GET /api/v1/catalogos/NACIONALIDAD"));
        // Validar unicidad
        if (clienteRepository.existsByCurp(request.getCurp())) {
            throw new CurpDuplicadaException("La CURP ya se encuentra registrada");
        }
        if (clienteRepository.existsByRfc(request.getRfc())) {
            throw new RfcDuplicadoException("El RFC ya se encuentra registrado");
        }
        if (clienteRepository.existsByCorreo(request.getCorreo())) {
            throw new CorreoDuplicadoException("El correo electrónico ya se encuentra registrado");
        }

        // Validar que el Estado exista en el catálogo oficial de entidades federativas
        boolean estadoValido = catalogoRepository.existsByTipoAndDescripcionIgnoreCaseAndActivoTrue("ESTADO", request.getEstado().trim())
                || catalogoRepository.existsByTipoAndClaveIgnoreCaseAndActivoTrue("ESTADO", request.getEstado().trim());
        if (!estadoValido) {
            throw new ValidacionException("El estado '" + request.getEstado() + "' no es válido o no existe en el catálogo oficial de entidades federativas.");
        }
    }

    private void mapearDatosPersonales(ClienteRequestDTO req, Cliente c) {
        c.setNombre(req.getNombre());
        c.setSegundoNombre(req.getSegundoNombre());
        c.setApellidoPaterno(req.getApellidoPaterno());
        c.setApellidoMaterno(req.getApellidoMaterno());
        c.setFechaNacimiento(parseFecha(req.getFechaNacimiento()));
        c.setCurp(req.getCurp());
        c.setRfc(req.getRfc());
        c.setSexo(req.getSexo());
        // Resolver descripción de nacionalidad desde el catálogo
        String descNacionalidad = catalogoRepository.findById(req.getNacionalidadId())
                .map(cat -> cat.getDescripcion())
                .orElse("Desconocida");
        c.setNacionalidad(descNacionalidad);
        c.setEstadoCivil(req.getEstadoCivil());
        c.setCorreo(req.getCorreo());
        c.setTelefonoMovil(req.getTelefonoMovil());
        c.setTelefonoAlternativo(req.getTelefonoAlternativo());
        c.setOcupacion(req.getOcupacion());
        c.setEmpresa(req.getEmpresa());
        c.setIngresoMensual(req.getIngresoMensual());
        c.setActivo(true);
    }

    private void mapearDomicilio(ClienteRequestDTO req, Domicilio d) {
        d.setCalle(req.getCalle());
        d.setNumeroExterior(req.getNumeroExterior());
        d.setNumeroInterior(req.getNumeroInterior());
        d.setColonia(req.getColonia());
        d.setMunicipio(req.getMunicipio());
        d.setEstado(req.getEstado());
        d.setCodigoPostal(req.getCodigoPostal());
        d.setPais(req.getPais());
        d.setActivo(true);
    }

    private Cuenta crearCuentaBancaria(Cliente cliente) {
        Cuenta cuenta = new Cuenta();
        cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta(generarNumeroCuentaUnico());
        cuenta.setEstatus(EstatusCuentaEnum.ACTIVA.getCodigo());
        
        Saldo saldo = new Saldo();
        saldo.setCuenta(cuenta);
        // Saldo inicial definido por el sistema
        saldo.setSaldo(new BigDecimal("100.00")); 
        saldo.setConcepto("SALDO INICIAL POR APERTURA");
        
        cuenta.setSaldo(saldo);
        return cuenta;
    }

    private String generarNumeroCuentaUnico() {
        Random random = new Random();
        String numero;
        do {
            // Generar 10 dígitos aleatorios
            long randNum = (long) (random.nextDouble() * 9_000_000_000L) + 1_000_000_000L;
            numero = String.valueOf(randNum);
        } while (cuentaRepository.existsByNumeroCuenta(numero));
        return numero;
    }

    private LoginCliente crearLogin(Cliente cliente, String usuario, String contrasenaRaw) {
        LoginCliente login = new LoginCliente();
        login.setCliente(cliente);
        login.setUsuario(usuario);
        login.setContrasenaHash(cifrarSHA256(contrasenaRaw));
        login.setActivo(true);
        login.setBloqueado(false);
        login.setIntentosFallidos(0);
        return login;
    }

    private void crearDatoBiometrico(Cliente cliente, String hash) {
        DatoBiometrico db = new DatoBiometrico();
        db.setCliente(cliente);
        db.setTipo("FACIAL_MEDIAPIPE");
        db.setHashBiometrico(hash);
        db.setActivo(true);
        biometricoRepository.save(db);
    }

    private String cifrarSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error cifrando contraseña", e);
        }
    }

    private ClienteResponseDTO mapToResponse(Cliente cliente) {
        ClienteResponseDTO dto = new ClienteResponseDTO();
        dto.setNombre(cliente.getNombre());
        dto.setSegundoNombre(cliente.getSegundoNombre());
        dto.setApellidoPaterno(cliente.getApellidoPaterno());
        dto.setApellidoMaterno(cliente.getApellidoMaterno());
        dto.setFechaNacimiento(cliente.getFechaNacimiento());
        dto.setCurp(cliente.getCurp());
        dto.setRfc(cliente.getRfc());
        dto.setSexo(cliente.getSexo());
        dto.setNacionalidad(cliente.getNacionalidad());
        dto.setEstadoCivil(cliente.getEstadoCivil());
        dto.setCorreo(cliente.getCorreo());
        dto.setTelefonoMovil(cliente.getTelefonoMovil());
        dto.setTelefonoAlternativo(cliente.getTelefonoAlternativo());
        dto.setOcupacion(cliente.getOcupacion());
        dto.setEmpresa(cliente.getEmpresa());
        dto.setIngresoMensual(cliente.getIngresoMensual());
        dto.setActivo(cliente.getActivo());
        dto.setFechaRegistro(cliente.getFechaRegistro());

        if (cliente.getDomicilio() != null) {
            dto.setCalle(cliente.getDomicilio().getCalle());
            dto.setNumeroExterior(cliente.getDomicilio().getNumeroExterior());
            dto.setNumeroInterior(cliente.getDomicilio().getNumeroInterior());
            dto.setColonia(cliente.getDomicilio().getColonia());
            dto.setMunicipio(cliente.getDomicilio().getMunicipio());
            dto.setEstado(cliente.getDomicilio().getEstado());
            dto.setCodigoPostal(cliente.getDomicilio().getCodigoPostal());
            dto.setPais(cliente.getDomicilio().getPais());
        }

        if (cliente.getCuenta() != null) {
            dto.setNumeroCuenta(cliente.getCuenta().getNumeroCuenta());
            dto.setEstatusCuenta(cliente.getCuenta().getEstatus());
        }

        dto.setId(cliente.getId());
        dto.setCodigo(0);
        dto.setMensaje("Operación exitosa");

        return dto;
    }

    private CuentaResponseDTO mapToCuentaResponse(Cuenta cuenta) {
        CuentaResponseDTO dto = new CuentaResponseDTO();
        dto.setNumeroCuenta(cuenta.getNumeroCuenta());
        dto.setEstatus(cuenta.getEstatus());
        dto.setFechaApertura(cuenta.getFechaApertura());
        dto.setCodigo(0);
        dto.setMensaje("Consulta exitosa");
        
        if (cuenta.getSaldo() != null) {
            dto.setSaldo(cuenta.getSaldo().getSaldo());
        } else {
            dto.setSaldo(BigDecimal.ZERO);
        }
        
        if (cuenta.getCliente() != null) {
            dto.setNombreCliente(cuenta.getCliente().getNombre() + " " + cuenta.getCliente().getApellidoPaterno());
        }
        
        return dto;
    }
    
    private LocalDate parseFecha(String fechaStr) {
        if (fechaStr == null) return null;
        try {
            LocalDate fecha = LocalDate.parse(fechaStr);
            return fecha;
        } catch (java.time.format.DateTimeParseException e) {
            throw new ValidacionException("La fecha '" + fechaStr + "' no es válida. Use el formato yyyy-MM-dd con valores reales (ej: 2024-02-29 pero no 2024-02-30)");
        }
    }
}
