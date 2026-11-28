package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.clientes.Domicilio;
import com.proyecto.servicios.entity.clientes.LoginCliente;
import com.proyecto.servicios.entity.clientes.Saldo;
import com.proyecto.servicios.enums.EstatusCuentaEnum;
import com.proyecto.servicios.exception.Autenticacion2FAException;
import com.proyecto.servicios.exception.CorreoDuplicadoException;
import com.proyecto.servicios.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.exception.CurpDuplicadaException;
import com.proyecto.servicios.exception.RfcDuplicadoException;
import com.proyecto.servicios.exception.ValidacionException;
import com.proyecto.servicios.model.auth.AbonoSaldoRequestDTO;
import com.proyecto.servicios.model.auth.ActualizarContactoRequestDTO;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequestDTO;
import com.proyecto.servicios.model.auth.DisponibilidadRegistroResponseDTO;
import com.proyecto.servicios.model.auth.EnviarCodigoRegistroRequestDTO;
import com.proyecto.servicios.model.auth.EnviarCodigoRegistroResponseDTO;
import com.proyecto.servicios.model.auth.LoginRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.RegisterRequestDTO;
import com.proyecto.servicios.model.auth.TotpEstadoResponseDTO;
import com.proyecto.servicios.model.auth.TotpSetupRequestDTO;
import com.proyecto.servicios.model.auth.TotpSetupResponseDTO;
import com.proyecto.servicios.model.auth.TotpVerificarRequestDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;
import com.proyecto.servicios.model.brevo.EmailEnvioResultadoDTO;
import com.proyecto.servicios.repositorys.clientes.ClienteRepository;
import com.proyecto.servicios.repositorys.clientes.CuentaRepository;
import com.proyecto.servicios.repositorys.clientes.LoginClienteRepository;
import com.proyecto.servicios.repositorys.clientes.SaldoRepository;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.ComprobanteEmailService;
import com.proyecto.servicios.service.PortalCuentaService;
import com.proyecto.servicios.util.TelefonoUtil;
import com.proyecto.servicios.util.TotpAuthenticatorUtil;
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
    private static final long VIGENCIA_CODIGO_REGISTRO_SEGUNDOS = 300L;
    private static final int MAX_INTENTOS_OTP_REGISTRO = 5;

    private final java.util.Map<String, CodigoRegistroTemporal> codigosRegistro = new java.util.concurrent.ConcurrentHashMap<>();

    private final ClienteRepository clienteRepository;
    private final LoginClienteRepository loginClienteRepository;
    private final CuentaRepository cuentaRepository;
    private final SaldoRepository saldoRepository;
    private final AuthService authService;
    private final ComprobanteEmailService comprobanteEmailService;
    private final PlatformTransactionManager transactionManager;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.onboarding.saldo-inicial:0.00}")
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    @Autowired(required = false)
    private com.proyecto.servicios.service.SepomexCatalogoService sepomexCatalogoService;

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

    private boolean correoYaEnUso(String correoLimpio) {
        return clienteRepository.existsByCorreo(correoLimpio)
                || clienteRepository.existsByCorreoIgnoreCase(correoLimpio);
    }

    private boolean usuarioYaEnUso(String usuarioLimpio) {
        return loginClienteRepository.findByUsuario(usuarioLimpio).isPresent()
                || loginClienteRepository.findByUsuarioIgnoreCase(usuarioLimpio).isPresent();
    }

    private boolean curpYaEnUso(String curpLimpia) {
        return clienteRepository.existsByCurp(curpLimpia)
                || clienteRepository.existsByCurpIgnoreCase(curpLimpia);
    }

    private boolean rfcYaEnUso(String rfcLimpio) {
        return clienteRepository.existsByRfc(rfcLimpio)
                || clienteRepository.existsByRfcIgnoreCase(rfcLimpio);
    }

    @Override
    @Transactional(readOnly = true)
    public DisponibilidadRegistroResponseDTO verificarDisponibilidadRegistro(
            String correo, String usuario, String curp, String rfc) {
        java.util.Map<String, String> mensajes = new java.util.LinkedHashMap<>();
        Boolean correoDisp = null;
        Boolean usuarioDisp = null;
        Boolean curpDisp = null;
        Boolean rfcDisp = null;

        if (correo != null && !correo.isBlank()) {
            String mailClean = correo.trim().toLowerCase(Locale.ROOT);
            boolean usado = correoYaEnUso(mailClean);
            correoDisp = !usado;
            if (usado) {
                mensajes.put("correo", "Este correo electrónico ya está en uso en otra cuenta. Inicia sesión o utiliza otro correo.");
            }
        }
        if (usuario != null && !usuario.isBlank()) {
            String usrClean = usuario.trim();
            boolean usado = usuarioYaEnUso(usrClean);
            usuarioDisp = !usado;
            if (usado) {
                mensajes.put("usuario", "El nombre de usuario '" + usrClean + "' ya se encuentra en uso.");
            }
        }
        if (curp != null && !curp.isBlank()) {
            String curpClean = curp.trim().toUpperCase(Locale.ROOT);
            boolean usado = curpYaEnUso(curpClean);
            curpDisp = !usado;
            if (usado) {
                mensajes.put("curp", "La CURP ingresada ya se encuentra registrada en otra cuenta.");
            }
        }
        if (rfc != null && !rfc.isBlank()) {
            String rfcClean = rfc.trim().toUpperCase(Locale.ROOT);
            boolean usado = rfcYaEnUso(rfcClean);
            rfcDisp = !usado;
            if (usado) {
                mensajes.put("rfc", "El RFC ingresado ya se encuentra registrado en otra cuenta.");
            }
        }

        return DisponibilidadRegistroResponseDTO.builder()
                .correoDisponible(correoDisp)
                .usuarioDisponible(usuarioDisp)
                .curpDisponible(curpDisp)
                .rfcDisponible(rfcDisp)
                .mensajes(mensajes)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TotpSetupResponseDTO generarConfiguracionTotpRegistro(TotpSetupRequestDTO request) {
        String correo = (request != null && request.getCorreo() != null)
                ? request.getCorreo().trim().toLowerCase(Locale.ROOT)
                : "";
        String usuario = (request != null && request.getUsuario() != null)
                ? request.getUsuario().trim()
                : "";

        if (correo.isBlank()) {
            throw new ValidacionException("El correo electrónico es obligatorio para configurar Google Authenticator.");
        }
        if (!TelefonoUtil.esCorreoSintaxisValida(correo)) {
            throw new ValidacionException("El formato del correo electrónico no es válido.");
        }
        if (TelefonoUtil.esCorreoPlaceholder(correo)) {
            throw new ValidacionException("Ingresa un correo electrónico real (no se permiten dominios de ejemplo).");
        }
        if (correoYaEnUso(correo)) {
            throw new CorreoDuplicadoException(
                    "Este correo electrónico ya está en uso en otra cuenta. Inicia sesión o utiliza otro correo.");
        }
        if (!usuario.isBlank() && usuarioYaEnUso(usuario)) {
            throw new ValidacionException("El nombre de usuario '" + usuario + "' ya se encuentra en uso.");
        }

        String etiquetaCuenta = !usuario.isBlank() ? usuario : correo;
        String secret = TotpAuthenticatorUtil.generarSecretoBase32();
        String uri = TotpAuthenticatorUtil.construirOtpAuthUri(
                TotpAuthenticatorUtil.EMISOR_DEFAULT, etiquetaCuenta, secret);
        String qrBase64 = TotpAuthenticatorUtil.generarQrPngBase64(uri, 280);

        return TotpSetupResponseDTO.builder()
                .secret(secret)
                .secretFormateado(TotpAuthenticatorUtil.formatearSecretoLegible(secret))
                .otpauthUri(uri)
                .qrPngBase64(qrBase64)
                .emisor(TotpAuthenticatorUtil.EMISOR_DEFAULT)
                .cuenta(etiquetaCuenta)
                .digitos(TotpAuthenticatorUtil.DIGITOS_TOTP)
                .periodoSegundos(TotpAuthenticatorUtil.PERIODO_SEGUNDOS)
                .build();
    }

    @Override
    public EnviarCodigoRegistroResponseDTO enviarCodigoVerificacionRegistro(EnviarCodigoRegistroRequestDTO request) {
        if (request == null || request.getCorreo() == null || request.getCorreo().isBlank()) {
            throw new ValidacionException("El correo electrónico es obligatorio para enviar el código de verificación.");
        }
        String correo = request.getCorreo().trim().toLowerCase(Locale.ROOT);
        if (!TelefonoUtil.esCorreoSintaxisValida(correo)) {
            throw new ValidacionException("El formato del correo electrónico no es válido.");
        }
        if (TelefonoUtil.esCorreoPlaceholder(correo)) {
            throw new ValidacionException(
                    "Ingresa un correo electrónico real para recibir tu código 2FA (no se permiten dominios de ejemplo).");
        }
        if (correoYaEnUso(correo)) {
            throw new CorreoDuplicadoException("Este correo electrónico ya está en uso en otra cuenta. Inicia sesión o utiliza otro correo.");
        }
        if (request.getUsuario() != null && !request.getUsuario().isBlank()) {
            String usuario = request.getUsuario().trim();
            if (usuarioYaEnUso(usuario)) {
                throw new ValidacionException("El nombre de usuario '" + usuario + "' ya se encuentra en uso.");
            }
        }
        if (request.getCurp() != null && !request.getCurp().isBlank()) {
            String curp = request.getCurp().trim().toUpperCase(Locale.ROOT);
            if (!curp.matches("^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z\\d]\\d$")) {
                throw new ValidacionException("El formato de la CURP es inválido (debe tener 18 caracteres oficiales).");
            }
            if (curpYaEnUso(curp)) {
                throw new CurpDuplicadaException("La CURP ingresada ya se encuentra registrada en otra cuenta.");
            }
        }
        if (request.getRfc() != null && !request.getRfc().isBlank()) {
            String rfc = request.getRfc().trim().toUpperCase(Locale.ROOT);
            if (!rfc.matches("^[A-ZÑ&]{3,4}\\d{6}[A-Z\\d]{3}$")) {
                throw new ValidacionException("El formato del RFC es inválido (debe tener 12 o 13 caracteres con homoclave).");
            }
            if (rfcYaEnUso(rfc)) {
                throw new RfcDuplicadoException("El RFC ingresado ya se encuentra registrado en otra cuenta.");
            }
        }

        String codigo = String.valueOf(RANDOM.nextInt(900_000) + 100_000);
        String nombreSaludo = request.getNombre() != null ? request.getNombre().trim() : "";

        EmailEnvioResultadoDTO envio = comprobanteEmailService != null
                ? comprobanteEmailService.enviarCodigoRegistro2FA(correo, nombreSaludo, codigo, VIGENCIA_CODIGO_REGISTRO_SEGUNDOS)
                : null;

        if (envio == null || !envio.isEnviado()) {
            String detalle = (envio != null && envio.getMensaje() != null && !envio.getMensaje().isBlank())
                    ? envio.getMensaje()
                    : "No fue posible enviar el código de verificación 2FA al correo proporcionado.";
            throw new ValidacionException(detalle);
        }

        codigosRegistro.put(
                correo,
                new CodigoRegistroTemporal(
                        codigo,
                        LocalDateTime.now().plusSeconds(VIGENCIA_CODIGO_REGISTRO_SEGUNDOS),
                        0
                )
        );
        log.info("Código 2FA de registro enviado vía Brevo a {}", TelefonoUtil.enmascararCorreo(correo));

        return EnviarCodigoRegistroResponseDTO.builder()
                .codigo(0)
                .mensaje("Código de verificación 2FA enviado a tu correo electrónico.")
                .correoEnviado(true)
                .correoDestino(correo)
                .expiraEnSegundos(VIGENCIA_CODIGO_REGISTRO_SEGUNDOS)
                .build();
    }

    @Override
    public LoginResponseDTO registrarClientePortalConVerificacion(RegisterRequestDTO request) {
        if (request == null) {
            throw new ValidacionException("La solicitud de registro es obligatoria.");
        }
        String correo = request.getCorreo() != null ? request.getCorreo().trim().toLowerCase(Locale.ROOT) : "";
        if (!correo.isBlank() && correoYaEnUso(correo)) {
            throw new CorreoDuplicadoException(
                    "Este correo electrónico ya está en uso en otra cuenta. Inicia sesión o utiliza otro correo.");
        }

        String totpSecretRaw = request.getTotpSecret() != null
                ? TotpAuthenticatorUtil.normalizarSecretoBase32(request.getTotpSecret())
                : "";
        if (!totpSecretRaw.isEmpty()) {
            if (!TotpAuthenticatorUtil.esSecretoBase32Valido(totpSecretRaw)) {
                throw new ValidacionException("La clave secreta de Google Authenticator no es válida.");
            }
            String codigoTotp = (request.getCodigoTotp() != null && !request.getCodigoTotp().isBlank())
                    ? request.getCodigoTotp().trim()
                    : (request.getCodigoVerificacion() != null ? request.getCodigoVerificacion().trim() : "");
            if (!codigoTotp.matches("^\\d{6}$")) {
                throw new Autenticacion2FAException(
                        "Ingresa el código dinámico de 6 dígitos que aparece en Google Authenticator.");
            }
            if (!TotpAuthenticatorUtil.verificarCodigo(totpSecretRaw, codigoTotp)) {
                throw new Autenticacion2FAException(
                        "Código de Google Authenticator incorrecto o expirado. Verifica el código actual de 6 dígitos (se renueva cada 30 segundos).");
            }
            request.setTotpSecret(totpSecretRaw);
            LoginResponseDTO resp = registrarClientePortal(request);
            codigosRegistro.remove(correo);
            return resp;
        }

        String codigoRecibido = request.getCodigoVerificacion() != null ? request.getCodigoVerificacion().trim() : "";

        if (!codigoRecibido.matches("^\\d{6}$")) {
            throw new Autenticacion2FAException(
                    "Debes ingresar el código de verificación 2FA de 6 dígitos.");
        }

        CodigoRegistroTemporal guardado = codigosRegistro.get(correo);
        if (guardado == null || LocalDateTime.now().isAfter(guardado.expiraEn())) {
            codigosRegistro.remove(correo);
            throw new Autenticacion2FAException(
                    "No existe un código de verificación activo o ha expirado para este correo. Solicita uno nuevo.");
        }

        if (!guardado.codigo().equals(codigoRecibido)) {
            int intentos = guardado.intentosFallidos() + 1;
            if (intentos >= MAX_INTENTOS_OTP_REGISTRO) {
                codigosRegistro.remove(correo);
                throw new Autenticacion2FAException(
                        "Demasiados intentos incorrectos. Solicita un nuevo código de verificación 2FA.");
            }
            codigosRegistro.put(correo, new CodigoRegistroTemporal(guardado.codigo(), guardado.expiraEn(), intentos));
            throw new Autenticacion2FAException("Código de verificación 2FA incorrecto. Verifica el código enviado a tu correo.");
        }

        LoginResponseDTO resp = registrarClientePortal(request);
        codigosRegistro.remove(correo);
        return resp;
    }

    @Override
    public LoginResponseDTO registrarClientePortal(RegisterRequestDTO request) {
        if (request == null) {
            throw new ValidacionException("La solicitud de registro es obligatoria.");
        }

        String usuario = request.getUsuario().trim();
        String correo = request.getCorreo().trim().toLowerCase(Locale.ROOT);
        String telefono = request.getTelefonoMovil().trim();
        String nombreTrim = request.getNombre() != null ? request.getNombre().trim() : "";
        String paternoTrim = request.getApellidoPaterno() != null ? request.getApellidoPaterno().trim() : "";
        String maternoTrim = request.getApellidoMaterno() != null ? request.getApellidoMaterno().trim() : "";

        if (nombreTrim.length() < 2 || nombreTrim.length() > 40) {
            throw new ValidacionException("El nombre debe tener entre 2 y 40 letras.");
        }
        if (paternoTrim.length() < 3 || paternoTrim.length() > 40) {
            throw new ValidacionException("El apellido paterno debe tener entre 3 y 40 letras.");
        }
        if (!maternoTrim.isEmpty() && (maternoTrim.length() < 3 || maternoTrim.length() > 40)) {
            throw new ValidacionException("El apellido materno debe tener entre 3 y 40 letras.");
        }

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

        if (usuarioYaEnUso(usuario)) {
            throw new ValidacionException("El nombre de usuario '" + usuario + "' ya se encuentra en uso.");
        }
        if (correoYaEnUso(correo)) {
            throw new CorreoDuplicadoException("Este correo electrónico ya está en uso en otra cuenta. Inicia sesión o utiliza otro correo.");
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

        if (!correoNuevo.equalsIgnoreCase(cliente.getCorreo()) && correoYaEnUso(correoNuevo)) {
            throw new CorreoDuplicadoException("Este correo electrónico ya está en uso en otra cuenta.");
        }

        cliente.setCorreo(correoNuevo);
        cliente.setTelefonoMovil(telefonoNuevo);

        if (request.getNombre() != null) {
            String nombreClean = request.getNombre().trim();
            if (nombreClean.length() < 2 || nombreClean.length() > 40) {
                throw new ValidacionException("El nombre debe tener entre 2 y 40 letras.");
            }
            cliente.setNombre(nombreClean);
        }
        if (request.getSegundoNombre() != null) {
            String segClean = request.getSegundoNombre().trim();
            if (!segClean.isEmpty() && (segClean.length() < 2 || segClean.length() > 40)) {
                throw new ValidacionException("El segundo nombre debe tener entre 2 y 40 letras.");
            }
            cliente.setSegundoNombre(segClean.isEmpty() ? null : segClean);
        }
        if (request.getApellidoPaterno() != null) {
            String apPatClean = request.getApellidoPaterno().trim();
            if (apPatClean.length() < 3 || apPatClean.length() > 40) {
                throw new ValidacionException("El apellido paterno debe tener entre 3 y 40 letras.");
            }
            cliente.setApellidoPaterno(apPatClean);
        }
        if (request.getApellidoMaterno() != null) {
            String apMatClean = request.getApellidoMaterno().trim();
            if (!apMatClean.isEmpty() && (apMatClean.length() < 3 || apMatClean.length() > 40)) {
                throw new ValidacionException("El apellido materno debe tener entre 3 y 40 letras.");
            }
            cliente.setApellidoMaterno(apMatClean);
        }
        if (request.getOcupacion() != null && !request.getOcupacion().isBlank()) {
            cliente.setOcupacion(request.getOcupacion().trim().toUpperCase(Locale.ROOT));
        }
        if (request.getProfesion() != null && !request.getProfesion().isBlank()) {
            cliente.setProfesion(request.getProfesion().trim().toUpperCase(Locale.ROOT));
        }
        if (request.getEmpresa() != null && !request.getEmpresa().isBlank()) {
            cliente.setEmpresa(request.getEmpresa().trim().toUpperCase(Locale.ROOT));
        }
        Domicilio dom = cliente.getDomicilio();
        if (dom != null) {
            if (request.getCalle() != null && !request.getCalle().isBlank()) {
                dom.setCalle(request.getCalle().trim());
            }
            if (request.getNumeroExterior() != null && !request.getNumeroExterior().isBlank()) {
                dom.setNumeroExterior(request.getNumeroExterior().trim());
            }
            if (request.getNumeroInterior() != null) {
                String ni = request.getNumeroInterior().trim();
                dom.setNumeroInterior(ni.isEmpty() ? null : ni);
            }
            if (request.getColonia() != null && !request.getColonia().isBlank()) {
                dom.setColonia(request.getColonia().trim());
            }
            if (request.getMunicipio() != null && !request.getMunicipio().isBlank()) {
                dom.setMunicipio(request.getMunicipio().trim());
            }
            if (request.getEstado() != null && !request.getEstado().isBlank()) {
                dom.setEstado(request.getEstado().trim());
            }
            if (request.getCodigoPostal() != null && !request.getCodigoPostal().isBlank()) {
                dom.setCodigoPostal(request.getCodigoPostal().trim());
            }
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
        if (!AuthServiceImpl.esUsuarioAdmin(perfilActual.getUsuario())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Solo un administrador puede ingresar fondos a las cuentas bancarias.");
        }
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
    public TotpEstadoResponseDTO consultarEstadoTotp(String authHeader) {
        UserProfileDTO perfil = authService.obtenerPerfilAutenticado(authHeader, null);
        LoginCliente login = loginClienteRepository.findByUsuario(perfil.getUsuario())
                .orElseThrow(() -> new ValidacionException("No se encontró la sesión activa del usuario."));
        boolean activo = Boolean.TRUE.equals(login.getTotpHabilitado())
                && login.getTotpSecret() != null
                && !login.getTotpSecret().isBlank();
        return TotpEstadoResponseDTO.builder()
                .totpHabilitado(activo)
                .mensaje(activo
                        ? "Google Authenticator se encuentra activo en tu cuenta."
                        : "Google Authenticator aún no está vinculado a tu cuenta.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TotpSetupResponseDTO iniciarVinculacionTotp(String authHeader) {
        UserProfileDTO perfil = authService.obtenerPerfilAutenticado(authHeader, null);
        String etiqueta = perfil.getUsuario() != null ? perfil.getUsuario() : perfil.getEmail();
        String secret = TotpAuthenticatorUtil.generarSecretoBase32();
        String uri = TotpAuthenticatorUtil.construirOtpAuthUri(
                TotpAuthenticatorUtil.EMISOR_DEFAULT, etiqueta, secret);
        String qrBase64 = TotpAuthenticatorUtil.generarQrPngBase64(uri, 280);
        return TotpSetupResponseDTO.builder()
                .secret(secret)
                .secretFormateado(TotpAuthenticatorUtil.formatearSecretoLegible(secret))
                .otpauthUri(uri)
                .qrPngBase64(qrBase64)
                .emisor(TotpAuthenticatorUtil.EMISOR_DEFAULT)
                .cuenta(etiqueta)
                .digitos(TotpAuthenticatorUtil.DIGITOS_TOTP)
                .periodoSegundos(TotpAuthenticatorUtil.PERIODO_SEGUNDOS)
                .build();
    }

    @Override
    @Transactional
    public TotpEstadoResponseDTO activarTotp(String authHeader, TotpVerificarRequestDTO request) {
        if (request == null || request.getSecret() == null || request.getSecret().isBlank()) {
            throw new ValidacionException("La clave secreta de Google Authenticator es obligatoria.");
        }
        String secretClean = TotpAuthenticatorUtil.normalizarSecretoBase32(request.getSecret());
        if (!TotpAuthenticatorUtil.esSecretoBase32Valido(secretClean)) {
            throw new ValidacionException("La clave secreta de Google Authenticator no tiene formato Base32 válido.");
        }
        if (!TotpAuthenticatorUtil.verificarCodigo(secretClean, request.getCodigo())) {
            throw new Autenticacion2FAException(
                    "Código de Google Authenticator incorrecto o expirado. Ingresa el código actual de 6 dígitos que muestra tu aplicación.");
        }
        UserProfileDTO perfil = authService.obtenerPerfilAutenticado(authHeader, null);
        LoginCliente login = loginClienteRepository.findByUsuario(perfil.getUsuario())
                .orElseThrow(() -> new ValidacionException("No se encontró el usuario autenticado."));
        login.setTotpSecret(secretClean);
        login.setTotpHabilitado(true);
        loginClienteRepository.save(login);
        return TotpEstadoResponseDTO.builder()
                .totpHabilitado(true)
                .mensaje("Google Authenticator vinculado y activado exitosamente.")
                .build();
    }

    @Override
    @Transactional
    public TotpEstadoResponseDTO desactivarTotp(String authHeader, TotpVerificarRequestDTO request) {
        UserProfileDTO perfil = authService.obtenerPerfilAutenticado(authHeader, null);
        LoginCliente login = loginClienteRepository.findByUsuario(perfil.getUsuario())
                .orElseThrow(() -> new ValidacionException("No se encontró el usuario autenticado."));
        if (Boolean.TRUE.equals(login.getTotpHabilitado())
                && login.getTotpSecret() != null
                && !login.getTotpSecret().isBlank()) {
            String codigo = request != null ? request.getCodigo() : null;
            if (!TotpAuthenticatorUtil.verificarCodigo(login.getTotpSecret(), codigo)) {
                throw new Autenticacion2FAException(
                        "Ingresa un código válido actual de Google Authenticator para poder desactivarlo.");
            }
        }
        login.setTotpSecret(null);
        login.setTotpHabilitado(false);
        loginClienteRepository.save(login);
        return TotpEstadoResponseDTO.builder()
                .totpHabilitado(false)
                .mensaje("Google Authenticator desactivado.")
                .build();
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

        String nacionalidad = textoONull(request.getNacionalidad());
        String estadoCivil = textoONull(request.getEstadoCivil());
        String ocupacion = textoONull(request.getOcupacion());
        String profesion = textoONull(request.getProfesion());
        String empresa = textoONull(request.getEmpresa());
        String telAlt = textoONull(request.getTelefonoAlternativo());
        BigDecimal ingreso = (request.getIngresoMensual() != null && request.getIngresoMensual().compareTo(BigDecimal.ZERO) > 0)
                ? request.getIngresoMensual().setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal("15000.00");

        Cliente cliente = new Cliente();
        cliente.setNombre(nombre);
        cliente.setSegundoNombre(segundoNombre);
        cliente.setApellidoPaterno(apPaterno);
        cliente.setApellidoMaterno(apMaterno);
        cliente.setCurp(curp);
        cliente.setRfc(rfc);
        cliente.setFechaNacimiento(fechaNacimiento);
        cliente.setSexo(sexo);
        cliente.setNacionalidad(nacionalidad != null ? nacionalidad.toUpperCase(Locale.ROOT) : "MEXICANA");
        cliente.setEstadoCivil(estadoCivil != null ? estadoCivil.toUpperCase(Locale.ROOT) : "SOLTERO");
        cliente.setOcupacion(ocupacion != null ? ocupacion.toUpperCase(Locale.ROOT) : "EMPLEADO(A) DEL SECTOR PRIVADO");
        cliente.setProfesion(profesion != null ? profesion.toUpperCase(Locale.ROOT) : null);
        cliente.setEmpresa(empresa != null ? empresa.toUpperCase(Locale.ROOT) : "INDEPENDIENTE");
        cliente.setIngresoMensual(ingreso);
        cliente.setCorreo(correo);
        cliente.setTelefonoMovil(telefono);
        cliente.setTelefonoAlternativo(telAlt);
        cliente.setActivo(true);

        String calle = textoONull(request.getCalle());
        String numExt = textoONull(request.getNumeroExterior());
        String numInt = textoONull(request.getNumeroInterior());
        String colonia = textoONull(request.getColonia());
        String municipio = textoONull(request.getMunicipio());
        String estado = textoONull(request.getEstado());
        String cp = textoONull(request.getCodigoPostal());
        String pais = textoONull(request.getPais());

        if (sepomexCatalogoService != null && cp != null && !cp.isBlank()) {
            var cpInfo = sepomexCatalogoService.consultarCodigoPostal(cp);
            if (!cpInfo.isValido()) {
                throw new ValidacionException(cpInfo.getMensaje());
            }
            estado = cpInfo.getEstado();
            municipio = cpInfo.getMunicipio();
            if (colonia == null || !sepomexCatalogoService.perteneceColoniaACodigoPostal(cp, colonia)) {
                throw new ValidacionException(
                        "Selecciona una colonia válida que pertenezca al código postal " + cp + ".");
            }
        }

        Domicilio domicilio = new Domicilio();
        domicilio.setCliente(cliente);
        domicilio.setCalle(calle != null ? calle : "Av. Reforma Digital");
        domicilio.setNumeroExterior(numExt != null ? numExt : "100");
        domicilio.setNumeroInterior(numInt);
        domicilio.setColonia(colonia != null ? colonia : "Centro");
        domicilio.setMunicipio(municipio != null ? municipio : "Cuauhtémoc");
        domicilio.setEstado(estado != null ? estado : "Ciudad de México");
        domicilio.setCodigoPostal(cp != null ? cp : "06000");
        domicilio.setPais(pais != null ? pais : "México");
        domicilio.setActivo(true);
        cliente.setDomicilio(domicilio);

        BigDecimal montoInicial = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Cuenta cuenta = new Cuenta();
        cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta(numeroCuenta);
        cuenta.setEstatus(EstatusCuentaEnum.ACTIVA.getCodigo());

        Saldo saldo = new Saldo();
        saldo.setCuenta(cuenta);
        saldo.setSaldo(montoInicial);
        saldo.setConcepto("Apertura de cuenta digital en $0.00 (fondeo por administrador)");
        cuenta.setSaldo(saldo);
        cliente.setCuenta(cuenta);

        LoginCliente login = new LoginCliente();
        login.setCliente(cliente);
        login.setUsuario(usuario);
        login.setContrasenaHash(passwordEncoder.encode(request.getContrasena()));
        login.setActivo(true);
        if (request.getTotpSecret() != null && !request.getTotpSecret().isBlank()) {
            String cleanTotp = TotpAuthenticatorUtil.normalizarSecretoBase32(request.getTotpSecret());
            if (TotpAuthenticatorUtil.esSecretoBase32Valido(cleanTotp)) {
                login.setTotpSecret(cleanTotp);
                login.setTotpHabilitado(true);
            }
        }
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

    private static String textoONull(String valor) {
        if (valor == null) {
            return null;
        }
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }

    private record RegistroPersistido(Cliente cliente, Cuenta cuenta, BigDecimal saldoInicialAplicado) {
    }

    private record CodigoRegistroTemporal(String codigo, LocalDateTime expiraEn, int intentosFallidos) {
    }
}
