package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.clientes.Domicilio;
import com.proyecto.servicios.entity.clientes.LoginCliente;
import com.proyecto.servicios.entity.clientes.Saldo;
import com.proyecto.servicios.enums.EstatusCuentaEnum;
import com.proyecto.servicios.exception.CorreoDuplicadoException;
import com.proyecto.servicios.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.exception.CurpDuplicadaException;
import com.proyecto.servicios.exception.RfcDuplicadoException;
import com.proyecto.servicios.exception.ValidacionException;
import com.proyecto.servicios.model.auth.AbonoSaldoRequestDTO;
import com.proyecto.servicios.model.auth.ActualizarContactoRequestDTO;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequestDTO;
import com.proyecto.servicios.model.auth.LoginRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.RegisterRequestDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;
import com.proyecto.servicios.repositorys.clientes.ClienteRepository;
import com.proyecto.servicios.repositorys.clientes.CuentaRepository;
import com.proyecto.servicios.repositorys.clientes.LoginClienteRepository;
import com.proyecto.servicios.repositorys.clientes.SaldoRepository;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.ComprobanteEmailService;
import com.proyecto.servicios.service.PortalCuentaService;
import com.proyecto.servicios.util.TelefonoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Implementación del servicio de autoservicio comercial del portal de pagos.
 */
@Service
@Slf4j
public class PortalCuentaServiceImpl implements PortalCuentaService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALFANUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final DateTimeFormatter CURP_DATE_FMT = DateTimeFormatter.ofPattern("yyMMdd");

    private final ClienteRepository clienteRepository;
    private final LoginClienteRepository loginClienteRepository;
    private final CuentaRepository cuentaRepository;
    private final SaldoRepository saldoRepository;
    private final AuthService authService;
    private final ComprobanteEmailService comprobanteEmailService;
    private final PlatformTransactionManager transactionManager;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.onboarding.saldo-inicial:200.00}")
    private BigDecimal saldoInicial = new BigDecimal("200.00");

    public PortalCuentaServiceImpl(
            ClienteRepository clienteRepository,
            LoginClienteRepository loginClienteRepository,
            CuentaRepository cuentaRepository,
            SaldoRepository saldoRepository,
            AuthService authService,
            ComprobanteEmailService comprobanteEmailService) {
        this(clienteRepository, loginClienteRepository, cuentaRepository, saldoRepository,
                authService, comprobanteEmailService, null, new BCryptPasswordEncoder());
    }

    public PortalCuentaServiceImpl(
            ClienteRepository clienteRepository,
            LoginClienteRepository loginClienteRepository,
            CuentaRepository cuentaRepository,
            SaldoRepository saldoRepository,
            AuthService authService,
            ComprobanteEmailService comprobanteEmailService,
            PlatformTransactionManager transactionManager) {
        this(clienteRepository, loginClienteRepository, cuentaRepository, saldoRepository,
                authService, comprobanteEmailService, transactionManager, new BCryptPasswordEncoder());
    }

    @Autowired
    public PortalCuentaServiceImpl(
            ClienteRepository clienteRepository,
            LoginClienteRepository loginClienteRepository,
            CuentaRepository cuentaRepository,
            SaldoRepository saldoRepository,
            AuthService authService,
            ComprobanteEmailService comprobanteEmailService,
            PlatformTransactionManager transactionManager,
            PasswordEncoder passwordEncoder) {
        this.clienteRepository = clienteRepository;
        this.loginClienteRepository = loginClienteRepository;
        this.cuentaRepository = cuentaRepository;
        this.saldoRepository = saldoRepository;
        this.authService = authService;
        this.comprobanteEmailService = comprobanteEmailService;
        this.transactionManager = transactionManager;
        this.passwordEncoder = passwordEncoder != null ? passwordEncoder : new BCryptPasswordEncoder();
    }

    @Override
    public LoginResponseDTO registrarClientePortal(RegisterRequestDTO request) {
        if (request == null) {
            throw new ValidacionException("La solicitud de registro es obligatoria.");
        }

        String usuario = request.getUsuario().trim();
        String correo = request.getCorreo().trim().toLowerCase(Locale.ROOT);
        String telefono = request.getTelefonoMovil().trim();

        if (!TelefonoUtil.esTelefonoMexicanoValido(telefono)) {
            throw new ValidacionException(TelefonoUtil.diagnosticarTelefonoInvalido(telefono));
        }
        if (!TelefonoUtil.esCorreoSintaxisValida(correo)) {
            throw new ValidacionException("El formato del correo electrónico no es válido.");
        }
        if (TelefonoUtil.esCorreoPlaceholder(correo)) {
            throw new ValidacionException(
                    "Ingresa un correo electrónico real para recibir tus códigos 2FA y comprobantes (no se permiten dominios de ejemplo).");
        }

        LocalDate fechaNacimiento = parsearYValidarFechaNacimiento(request.getFechaNacimiento());

        if (loginClienteRepository.findByUsuario(usuario).isPresent()) {
            throw new ValidacionException("El nombre de usuario '" + usuario + "' ya se encuentra en uso.");
        }
        if (clienteRepository.existsByCorreo(correo)) {
            throw new CorreoDuplicadoException("El correo electrónico ya está registrado en otra cuenta.");
        }

        RegistroPersistido persistido = transactionManager != null
                ? new TransactionTemplate(transactionManager)
                        .execute(status -> persistirNuevoCliente(request, usuario, correo, telefono, fechaNacimiento))
                : persistirNuevoCliente(request, usuario, correo, telefono, fechaNacimiento);

        if (persistido == null) {
            throw new ValidacionException("No fue posible completar el registro del cliente.");
        }

        LoginResponseDTO loginResp = authService.login(new LoginRequestDTO(usuario, request.getContrasena()));
        loginResp.setTelefonoMovil(persistido.cliente().getTelefonoMovil());

        try {
            comprobanteEmailService.enviarCorreoBienvenida(
                    persistido.cliente(), persistido.cuenta(), usuario, persistido.saldoInicialAplicado());
        } catch (Exception e) {
            log.warn("No se pudo enviar correo de bienvenida a {}: {}",
                    TelefonoUtil.enmascararCorreo(correo), e.getMessage());
        }

        log.info("Registro comercial completado para usuario={}, cuenta={}",
                usuario, TelefonoUtil.enmascararCuenta(persistido.cuenta().getNumeroCuenta()));
        return loginResp;
    }

    @Override
    @Transactional
    public UserProfileDTO actualizarContacto(
            String authHeader, String usuarioParam, ActualizarContactoRequestDTO request) {
        if (request == null) {
            throw new ValidacionException("Los datos de contacto son obligatorios.");
        }

        UserProfileDTO perfilActual = authService.obtenerPerfilAutenticado(authHeader, usuarioParam);
        String correoNuevo = request.getCorreo().trim().toLowerCase(Locale.ROOT);
        String telefonoNuevo = request.getTelefonoMovil().trim();

        if (!TelefonoUtil.esTelefonoMexicanoValido(telefonoNuevo)) {
            throw new ValidacionException(TelefonoUtil.diagnosticarTelefonoInvalido(telefonoNuevo));
        }
        if (!TelefonoUtil.esCorreoSintaxisValida(correoNuevo)) {
            throw new ValidacionException("El formato del correo electrónico no es válido.");
        }
        if (TelefonoUtil.esCorreoPlaceholder(correoNuevo)) {
            throw new ValidacionException(
                    "Ingresa un correo electrónico real para recibir notificaciones (no se permiten dominios de ejemplo).");
        }

        Cliente cliente = clienteRepository.findById(perfilActual.getClienteId())
                .orElseThrow(() -> new ValidacionException("No se encontró el perfil del cliente autenticado."));

        if (!correoNuevo.equalsIgnoreCase(cliente.getCorreo()) && clienteRepository.existsByCorreo(correoNuevo)) {
            throw new CorreoDuplicadoException("El correo electrónico ya se encuentra asociado a otra cuenta.");
        }

        cliente.setCorreo(correoNuevo);
        cliente.setTelefonoMovil(telefonoNuevo);

        if (request.getNombre() != null) {
            String nombreClean = request.getNombre().trim();
            if (nombreClean.length() < 2) {
                throw new ValidacionException("El nombre debe tener al menos 2 caracteres.");
            }
            cliente.setNombre(nombreClean);
        }
        if (request.getSegundoNombre() != null) {
            String segClean = request.getSegundoNombre().trim();
            cliente.setSegundoNombre(segClean.isEmpty() ? null : segClean);
        }
        if (request.getApellidoPaterno() != null) {
            String apPatClean = request.getApellidoPaterno().trim();
            if (apPatClean.length() < 2) {
                throw new ValidacionException("El apellido paterno debe tener al menos 2 caracteres.");
            }
            cliente.setApellidoPaterno(apPatClean);
        }
        if (request.getApellidoMaterno() != null) {
            String apMatClean = request.getApellidoMaterno().trim();
            if (!apMatClean.isEmpty() && apMatClean.length() < 2) {
                throw new ValidacionException("El apellido materno debe tener al menos 2 caracteres.");
            }
            cliente.setApellidoMaterno(apMatClean);
        }

        clienteRepository.save(cliente);

        UserProfileDTO actualizado = authService.obtenerPerfilAutenticado(authHeader, usuarioParam);
        actualizado.setTelefonoMovil(telefonoNuevo);
        actualizado.setEmail(correoNuevo);
        actualizado.setNombre(cliente.getNombre());
        actualizado.setSegundoNombre(cliente.getSegundoNombre());
        actualizado.setApellidoPaterno(cliente.getApellidoPaterno());
        actualizado.setApellidoMaterno(cliente.getApellidoMaterno());
        actualizado.setNombreCompleto(construirNombreCompleto(cliente));
        return actualizado;
    }

    @Override
    @Transactional
    public void cambiarContrasena(
            String authHeader, String usuarioParam, CambiarContrasenaRequestDTO request) {
        if (request == null || request.getContrasenaActual() == null || request.getContrasenaNueva() == null) {
            throw new ValidacionException("Debes indicar tu contraseña actual y tu nueva contraseña.");
        }
        String nueva = request.getContrasenaNueva();
        if (!nueva.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$")) {
            throw new ValidacionException(
                    "La nueva contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula y un número.");
        }

        UserProfileDTO perfil = authService.obtenerPerfilAutenticado(authHeader, usuarioParam);
        LoginCliente login = loginClienteRepository.findByUsuario(perfil.getUsuario())
                .orElseThrow(() -> new com.proyecto.servicios.exception.AutenticacionException("Usuario no encontrado."));

        if (!coincideContrasena(request.getContrasenaActual(), login.getContrasenaHash())) {
            throw new com.proyecto.servicios.exception.AutenticacionException("La contraseña actual es incorrecta.");
        }

        if (coincideContrasena(nueva, login.getContrasenaHash())) {
            throw new ValidacionException("La nueva contraseña debe ser diferente a tu contraseña actual.");
        }

        login.setContrasenaHash(passwordEncoder.encode(nueva));
        loginClienteRepository.save(login);
        authService.invalidarOtrasSesiones(perfil.getUsuario(), extraerBearerToken(authHeader));
        log.info("Contraseña actualizada exitosamente para usuario={}", perfil.getUsuario());
    }

    private boolean coincideContrasena(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, storedHash);
        }
        String sha = AuthServiceImpl.cifrarSHA256(rawPassword);
        if (sha == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                storedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private String extraerBearerToken(String authHeader) {
        if (authHeader == null) {
            return null;
        }
        String trimmed = authHeader.trim();
        if (trimmed.length() >= 7 && trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            trimmed = trimmed.substring(7).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String construirNombreCompleto(Cliente c) {
        StringBuilder sb = new StringBuilder();
        if (c.getNombre() != null && !c.getNombre().isBlank()) sb.append(c.getNombre().trim());
        if (c.getSegundoNombre() != null && !c.getSegundoNombre().isBlank()) sb.append(" ").append(c.getSegundoNombre().trim());
        if (c.getApellidoPaterno() != null && !c.getApellidoPaterno().isBlank()) sb.append(" ").append(c.getApellidoPaterno().trim());
        if (c.getApellidoMaterno() != null && !c.getApellidoMaterno().isBlank()) sb.append(" ").append(c.getApellidoMaterno().trim());
        return sb.toString().trim();
    }

    @Override
    @Transactional
    public UserProfileDTO abonarSaldo(String authHeader, String usuarioParam, AbonoSaldoRequestDTO request) {
        if (request == null || request.getMonto() == null || request.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacionException("El monto del abono debe ser mayor a cero.");
        }

        UserProfileDTO perfilActual = authService.obtenerPerfilAutenticado(authHeader, usuarioParam);
        Cuenta cuenta = cuentaRepository.findByNumeroCuentaForUpdate(perfilActual.getNumeroCuenta())
                .orElseThrow(() -> new CuentaNoEncontradaException(
                        "No se encontró la cuenta bancaria asociada al perfil."));

        Saldo saldo = saldoRepository.findByCuentaId(cuenta.getId())
                .orElseThrow(() -> new CuentaNoEncontradaException("No se encontró el registro de saldo de la cuenta."));

        BigDecimal montoAbono = request.getMonto().setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoNuevo = saldo.getSaldo().add(montoAbono).setScale(2, RoundingMode.HALF_UP);
        saldo.setSaldo(saldoNuevo);
        saldo.setFechaMovimiento(LocalDateTime.now());
        String concepto = (request.getConcepto() != null && !request.getConcepto().isBlank())
                ? request.getConcepto().trim()
                : "Abono digital de fondos";
        saldo.setConcepto(concepto);
        saldoRepository.save(saldo);

        UserProfileDTO actualizado = authService.obtenerPerfilAutenticado(authHeader, usuarioParam);
        actualizado.setSaldo(saldoNuevo);
        if (cuenta.getCliente() != null) {
            actualizado.setTelefonoMovil(cuenta.getCliente().getTelefonoMovil());
        }
        return actualizado;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponseDTO enriquecerLoginConTelefono(LoginResponseDTO response) {
        if (response == null || response.getTelefonoMovil() != null || response.getClienteId() == null) {
            return response;
        }
        clienteRepository.findById(response.getClienteId())
                .ifPresent(cliente -> response.setTelefonoMovil(cliente.getTelefonoMovil()));
        return response;
    }

    private RegistroPersistido persistirNuevoCliente(
            RegisterRequestDTO request, String usuario, String correo, String telefono, LocalDate fechaNacimiento) {

        String sexo = (request.getSexo() != null && !request.getSexo().isBlank())
                ? request.getSexo().trim().toUpperCase(Locale.ROOT)
                : "HOMBRE";

        String nombre = request.getNombre().trim().toUpperCase(Locale.ROOT);
        String segundoNombre = (request.getSegundoNombre() != null && !request.getSegundoNombre().isBlank())
                ? request.getSegundoNombre().trim().toUpperCase(Locale.ROOT)
                : null;
        String apPaterno = request.getApellidoPaterno().trim().toUpperCase(Locale.ROOT);
        String apMaterno = request.getApellidoMaterno() == null ? "" : request.getApellidoMaterno().trim().toUpperCase(Locale.ROOT);

        String curp = resolverCurpUnica(request.getCurp(), nombre, apPaterno, apMaterno, fechaNacimiento, sexo);
        String rfc = resolverRfcUnico(request.getRfc(), curp);
        String numeroCuenta = generarNumeroCuentaUnico();

        Cliente cliente = new Cliente();
        cliente.setNombre(nombre);
        cliente.setSegundoNombre(segundoNombre);
        cliente.setApellidoPaterno(apPaterno);
        cliente.setApellidoMaterno(apMaterno);
        cliente.setCurp(curp);
        cliente.setRfc(rfc);
        cliente.setFechaNacimiento(fechaNacimiento);
        cliente.setSexo(sexo);
        cliente.setNacionalidad("MEXICANA");
        cliente.setEstadoCivil("SOLTERO");
        cliente.setOcupacion("PARTICULAR");
        cliente.setEmpresa("INDEPENDIENTE");
        cliente.setIngresoMensual(new BigDecimal("15000.00"));
        cliente.setCorreo(correo);
        cliente.setTelefonoMovil(telefono);
        cliente.setActivo(true);

        Domicilio domicilio = new Domicilio();
        domicilio.setCliente(cliente);
        domicilio.setCalle("Av. Reforma Digital");
        domicilio.setNumeroExterior("100");
        domicilio.setColonia("Centro");
        domicilio.setMunicipio("Cuauhtémoc");
        domicilio.setEstado("Ciudad de México");
        domicilio.setCodigoPostal("06000");
        domicilio.setPais("México");
        domicilio.setActivo(true);
        cliente.setDomicilio(domicilio);

        BigDecimal montoInicial = (saldoInicial != null && saldoInicial.compareTo(BigDecimal.ZERO) >= 0)
                ? saldoInicial.setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal("200.00");

        Cuenta cuenta = new Cuenta();
        cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta(numeroCuenta);
        cuenta.setEstatus(EstatusCuentaEnum.ACTIVA.getCodigo());

        Saldo saldo = new Saldo();
        saldo.setCuenta(cuenta);
        saldo.setSaldo(montoInicial);
        saldo.setConcepto("Saldo de bienvenida por apertura digital");
        cuenta.setSaldo(saldo);
        cliente.setCuenta(cuenta);

        LoginCliente login = new LoginCliente();
        login.setCliente(cliente);
        login.setUsuario(usuario);
        login.setContrasenaHash(passwordEncoder.encode(request.getContrasena()));
        login.setActivo(true);
        cliente.setLoginCliente(login);

        Cliente guardado = clienteRepository.save(cliente);
        return new RegistroPersistido(guardado, guardado.getCuenta(), montoInicial);
    }

    private LocalDate parsearYValidarFechaNacimiento(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.of(1995, 1, 1);
        }
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new ValidacionException("La fecha de nacimiento debe tener formato yyyy-MM-dd.");
        }
        LocalDate hoy = LocalDate.now();
        if (fecha.isAfter(hoy)) {
            throw new ValidacionException("La fecha de nacimiento no puede ser futura.");
        }
        int edad = Period.between(fecha, hoy).getYears();
        if (edad < 18 || edad > 110) {
            throw new ValidacionException("El titular debe tener entre 18 y 110 años para abrir una cuenta.");
        }
        return fecha;
    }

    private String resolverCurpUnica(
            String curpIngresada,
            String nombre,
            String apPaterno,
            String apMaterno,
            LocalDate fechaNacimiento,
            String sexo) {
        if (curpIngresada != null && !curpIngresada.isBlank()) {
            String curp = curpIngresada.trim().toUpperCase(Locale.ROOT);
            if (clienteRepository.existsByCurp(curp)) {
                throw new CurpDuplicadaException("La CURP ingresada ya se encuentra registrada.");
            }
            return curp;
        }

        String p = soloLetrasAscii(apPaterno);
        String m = soloLetrasAscii(apMaterno);
        String n = soloLetrasAscii(nombre);
        String prefijo = "" + p.charAt(0) + primeraVocalInterna(p) + m.charAt(0) + n.charAt(0);
        String fecha = fechaNacimiento.format(CURP_DATE_FMT);
        String gen = "MUJER".equals(sexo) ? "M" : "H";
        String sufijoBase = "DF" + primeraConsonanteInterna(p) + primeraConsonanteInterna(m) + primeraConsonanteInterna(n);

        for (int i = 0; i < 30; i++) {
            char c1 = ALFANUM.charAt(RANDOM.nextInt(ALFANUM.length()));
            char c2 = (char) ('0' + RANDOM.nextInt(10));
            String candidata = prefijo + fecha + gen + sufijoBase + c1 + c2;
            if (!clienteRepository.existsByCurp(candidata)) {
                return candidata;
            }
        }
        throw new ValidacionException("No fue posible generar una clave única para el cliente. Intenta nuevamente.");
    }

    private String resolverRfcUnico(String rfcIngresado, String curp) {
        if (rfcIngresado != null && !rfcIngresado.isBlank()) {
            String rfc = rfcIngresado.trim().toUpperCase(Locale.ROOT);
            if (clienteRepository.existsByRfc(rfc)) {
                throw new RfcDuplicadoException("El RFC ingresado ya se encuentra registrado.");
            }
            return rfc;
        }
        String base = curp.substring(0, 10);
        for (int i = 0; i < 30; i++) {
            StringBuilder homo = new StringBuilder(3);
            for (int j = 0; j < 3; j++) {
                homo.append(ALFANUM.charAt(RANDOM.nextInt(ALFANUM.length())));
            }
            String candidata = base + homo;
            if (!clienteRepository.existsByRfc(candidata)) {
                return candidata;
            }
        }
        throw new ValidacionException("No fue posible generar un RFC único. Intenta nuevamente.");
    }

    private String generarNumeroCuentaUnico() {
        for (int intento = 0; intento < 30; intento++) {
            StringBuilder sb = new StringBuilder(16);
            sb.append(RANDOM.nextBoolean() ? '4' : '5');
            for (int i = 1; i < 16; i++) {
                sb.append(RANDOM.nextInt(10));
            }
            String num = sb.toString();
            if (!cuentaRepository.existsByNumeroCuenta(num)) {
                return num;
            }
        }
        throw new ValidacionException("No fue posible asignar un número de cuenta único. Intenta nuevamente.");
    }

    private static String soloLetrasAscii(String texto) {
        String norm = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z]", "");
        return norm.isEmpty() ? "X" : norm;
    }

    private static char primeraVocalInterna(String texto) {
        for (int i = 1; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U') {
                return c;
            }
        }
        return 'X';
    }

    private static char primeraConsonanteInterna(String texto) {
        for (int i = 1; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c != 'A' && c != 'E' && c != 'I' && c != 'O' && c != 'U') {
                return c;
            }
        }
        return 'X';
    }

    private record RegistroPersistido(Cliente cliente, Cuenta cuenta, BigDecimal saldoInicialAplicado) {
    }
}
