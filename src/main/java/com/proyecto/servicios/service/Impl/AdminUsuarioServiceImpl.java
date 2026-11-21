package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.*;
import com.proyecto.servicios.entity.gestopago.PagoServicio;
import com.proyecto.servicios.enums.EstatusCuentaEnum;
import com.proyecto.servicios.exception.*;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.admin.*;
import com.proyecto.servicios.model.auth.*;
import com.proyecto.servicios.repositorys.clientes.*;
import com.proyecto.servicios.repositorys.gestopago.PagoServicioRepository;
import com.proyecto.servicios.security.AccountOwnershipValidator;
import com.proyecto.servicios.service.*;
import com.proyecto.servicios.util.TelefonoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminUsuarioServiceImpl implements AdminUsuarioService {

    public static final String ADMIN_USERNAME = "admin";
    private static final String TIPO_HUELLA = "HUELLA_DISPOSITIVO";

    private final ClienteRepository clienteRepo;
    private final LoginClienteRepository loginRepo;
    private final CuentaRepository cuentaRepo;
    private final SaldoRepository saldoRepo;
    private final DatoBiometricoRepository bioRepo;
    private final PagoServicioRepository pagoRepo;
    private final PortalCuentaService portalService;
    private final AuthService authService;
    private final AccountOwnershipValidator ownershipValidator;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.password:Admin2026#}")
    private String adminDefaultPassword = "Admin2026#";

    @Autowired
    public AdminUsuarioServiceImpl(
            ClienteRepository clienteRepo,
            LoginClienteRepository loginRepo,
            CuentaRepository cuentaRepo,
            SaldoRepository saldoRepo,
            DatoBiometricoRepository bioRepo,
            PagoServicioRepository pagoRepo,
            PortalCuentaService portalService,
            AuthService authService,
            AccountOwnershipValidator ownershipValidator,
            PasswordEncoder passwordEncoder) {
        this.clienteRepo = clienteRepo;
        this.loginRepo = loginRepo;
        this.cuentaRepo = cuentaRepo;
        this.saldoRepo = saldoRepo;
        this.bioRepo = bioRepo;
        this.pagoRepo = pagoRepo;
        this.portalService = portalService;
        this.authService = authService;
        this.ownershipValidator = ownershipValidator;
        this.passwordEncoder = passwordEncoder != null ? passwordEncoder : new BCryptPasswordEncoder();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onReady() {
        try {
            asegurarUsuarioAdminInicial();
        } catch (Exception ex) {
            log.warn("Aviso en verificacion inicial de cuenta admin: {}", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public void asegurarUsuarioAdminInicial() {
        Optional<LoginCliente> found = loginRepo.findByUsuario(ADMIN_USERNAME);
        if (found.isPresent()) {
            LoginCliente lg = found.get();
            lg.setActivo(true);
            lg.setBloqueado(false);
            lg.setIntentosFallidos(0);
            loginRepo.save(lg);
            if (lg.getCliente() != null && !Boolean.TRUE.equals(lg.getCliente().getActivo())) {
                lg.getCliente().setActivo(true);
                clienteRepo.save(lg.getCliente());
            }
            return;
        }

        Cliente c = new Cliente();
        c.setNombre("ADMINISTRADOR");
        c.setSegundoNombre("GENERAL");
        c.setApellidoPaterno("BANCO");
        c.setApellidoMaterno("DIGITAL");
        c.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        c.setCurp(clienteRepo.existsByCurp("ADMG900101HDFRSL09") ? "ADMG900101HDFRSL98" : "ADMG900101HDFRSL09");
        c.setRfc(clienteRepo.existsByRfc("ADMG900101ADM") ? "ADMG900101AD8" : "ADMG900101ADM");
        c.setSexo("HOMBRE");
        c.setNacionalidad("MEXICANA");
        c.setEstadoCivil("SOLTERO");
        c.setOcupacion("ADMIN");
        c.setEmpresa("BANCO DIGITAL");
        c.setIngresoMensual(new BigDecimal("50000.00"));
        c.setCorreo(clienteRepo.existsByCorreo("admin@bancodigital.com.mx")
                ? "root.admin@bancodigital.com.mx"
                : "admin@bancodigital.com.mx");
        c.setTelefonoMovil("5523456789");
        c.setActivo(true);

        Domicilio dom = new Domicilio();
        dom.setCliente(c);
        dom.setCalle("Reforma");
        dom.setNumeroExterior("505");
        dom.setColonia("Centro");
        dom.setMunicipio("Cuauhtemoc");
        dom.setEstado("Ciudad de México");
        dom.setCodigoPostal("06500");
        dom.setPais("México");
        dom.setActivo(true);
        c.setDomicilio(dom);

        Cuenta cta = new Cuenta();
        cta.setCliente(c);
        cta.setNumeroCuenta(cuentaRepo.existsByNumeroCuenta("4000000000000001") ? "4000000000009998" : "4000000000000001");
        cta.setEstatus(EstatusCuentaEnum.ACTIVA.getCodigo());

        Saldo s = new Saldo();
        s.setCuenta(cta);
        s.setSaldo(new BigDecimal("10000.00"));
        s.setConcepto("Saldo inicial cuenta admin");
        cta.setSaldo(s);
        c.setCuenta(cta);

        String pwd = (adminDefaultPassword != null && !adminDefaultPassword.isBlank()) ? adminDefaultPassword : "Admin2026#";
        LoginCliente lg = new LoginCliente();
        lg.setCliente(c);
        lg.setUsuario(ADMIN_USERNAME);
        lg.setContrasenaHash(passwordEncoder.encode(pwd));
        lg.setActivo(true);
        lg.setBloqueado(false);
        lg.setIntentosFallidos(0);
        c.setLoginCliente(lg);

        clienteRepo.save(c);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminUsuarioDTO> listarTodosLosUsuarios(String adminAutenticado) {
        ownershipValidator.validarPrivilegiosAdmin(adminAutenticado);
        List<Object[]> rows = clienteRepo.findAllResumenAdminRaw();
        if (rows != null && !rows.isEmpty()) {
            return rows.stream()
                    .map(this::mapRawRowToDto)
                    .collect(Collectors.toList());
        }
        return clienteRepo.findAll(Sort.by(Sort.Direction.DESC, "id"))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private AdminUsuarioDTO mapRawRowToDto(Object[] row) {
        Long id = row[0] != null ? ((Number) row[0]).longValue() : null;
        String usr = row[1] != null ? row[1].toString() : null;
        String nombre = row[2] != null ? row[2].toString() : "";
        String segundo = row[3] != null ? row[3].toString() : null;
        String pat = row[4] != null ? row[4].toString() : "";
        String mat = row[5] != null ? row[5].toString() : "";

        LocalDate fechaNac = null;
        if (row[6] instanceof LocalDate ld) {
            fechaNac = ld;
        } else if (row[6] instanceof java.sql.Date sd) {
            fechaNac = sd.toLocalDate();
        } else if (row[6] != null) {
            try {
                fechaNac = LocalDate.parse(row[6].toString());
            } catch (Exception ignored) {
            }
        }

        String sexo = row[7] != null ? row[7].toString() : null;
        String curp = row[8] != null ? row[8].toString() : null;
        String rfc = row[9] != null ? row[9].toString() : null;
        String correo = row[10] != null ? row[10].toString() : "";
        String tel = row[11] != null ? row[11].toString() : "";
        String numCuenta = row[12] != null ? row[12].toString() : null;
        String estatusCuenta = row[13] != null ? row[13].toString() : "SIN_CUENTA";
        BigDecimal saldo = BigDecimal.ZERO;
        if (row[14] instanceof BigDecimal bd) {
            saldo = bd;
        } else if (row[14] instanceof Number n) {
            saldo = BigDecimal.valueOf(n.doubleValue());
        }
        boolean activo = row[15] instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(row[15]));
        boolean bloqueado = row[16] instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(row[16]));
        int intentos = row[17] instanceof Number n ? n.intValue() : 0;
        boolean huella = row[18] instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(row[18]));
        LocalDateTime ultimoAcceso = toLocalDateTime(row[19]);
        LocalDateTime fechaRegistro = toLocalDateTime(row[20]);

        boolean admin = AuthServiceImpl.esUsuarioAdmin(usr);
        StringBuilder full = new StringBuilder();
        if (!nombre.isBlank()) full.append(nombre.trim());
        if (segundo != null && !segundo.isBlank()) full.append(" ").append(segundo.trim());
        if (!pat.isBlank()) full.append(" ").append(pat.trim());
        if (!mat.isBlank()) full.append(" ").append(mat.trim());

        return AdminUsuarioDTO.builder()
                .clienteId(id)
                .usuario(usr)
                .rol(admin ? "ADMIN" : "CLIENTE")
                .esAdmin(admin)
                .nombre(nombre)
                .segundoNombre(segundo)
                .apellidoPaterno(pat)
                .apellidoMaterno(mat)
                .nombreCompleto(full.toString().trim())
                .fechaNacimiento(fechaNac)
                .sexo(sexo)
                .curp(curp)
                .rfc(rfc)
                .correo(correo)
                .telefonoMovil(tel)
                .numeroCuenta(numCuenta)
                .estatusCuenta(estatusCuenta)
                .saldo(saldo)
                .activo(activo)
                .bloqueado(bloqueado)
                .intentosFallidos(intentos)
                .biometriaActiva(huella)
                .ultimoAcceso(ultimoAcceso)
                .fechaRegistro(fechaRegistro)
                .build();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUsuarioDTO obtenerUsuarioPorId(String adminAutenticado, Long clienteId) {
        ownershipValidator.validarPrivilegiosAdmin(adminAutenticado);
        Cliente c = clienteRepo.findById(clienteId)
                .orElseThrow(() -> new ClienteNoEncontradoException("Usuario no encontrado con ID: " + clienteId));
        return toDto(c);
    }

    @Override
    @Transactional
    public AdminUsuarioDTO crearUsuarioComoAdmin(String adminAutenticado, RegisterRequestDTO request) {
        ownershipValidator.validarPrivilegiosAdmin(adminAutenticado);
        LoginResponseDTO resp = portalService.registrarClientePortal(request);
        if (resp != null && resp.getToken() != null) {
            try {
                authService.logout(resp.getToken(), resp.getUsuario());
            } catch (Exception ignored) {
            }
        }
        Long id = resp != null ? resp.getClienteId() : null;
        if (id == null) {
            throw new ValidacionException("No se pudo obtener el ID del usuario creado.");
        }
        return obtenerUsuarioPorId(adminAutenticado, id);
    }

    @Override
    @Transactional
    public AdminUsuarioDTO actualizarUsuarioComoAdmin(
            String adminAutenticado,
            Long clienteId,
            AdminActualizarUsuarioRequestDTO req) {
        ownershipValidator.validarPrivilegiosAdmin(adminAutenticado);
        if (req == null) {
            throw new ValidacionException("Solicitud vacia.");
        }

        Cliente c = clienteRepo.findById(clienteId)
                .orElseThrow(() -> new ClienteNoEncontradoException("Usuario no encontrado con ID: " + clienteId));
        LoginCliente lg = resolveLogin(c);
        boolean targetIsAdmin = lg != null && AuthServiceImpl.esUsuarioAdmin(lg.getUsuario());

        aplicarCambiosPersonalesYContacto(c, req);
        if (lg != null) {
            aplicarCambiosCredenciales(lg, req, targetIsAdmin);
        }
        aplicarCambiosCuentaYSaldo(c, lg, req, targetIsAdmin, adminAutenticado);

        if (lg != null) {
            loginRepo.save(lg);
        }
        return toDto(clienteRepo.save(c));
    }

    private void aplicarCambiosPersonalesYContacto(Cliente c, AdminActualizarUsuarioRequestDTO req) {
        if (req.getNombre() != null && !req.getNombre().isBlank()) {
            String v = req.getNombre().trim().toUpperCase(Locale.ROOT);
            if (v.length() < 2) throw new ValidacionException("El nombre requiere minimo 2 caracteres.");
            c.setNombre(v);
        }
        if (req.getSegundoNombre() != null) {
            String v = req.getSegundoNombre().trim().toUpperCase(Locale.ROOT);
            c.setSegundoNombre(v.isEmpty() ? null : v);
        }
        if (req.getApellidoPaterno() != null && !req.getApellidoPaterno().isBlank()) {
            String v = req.getApellidoPaterno().trim().toUpperCase(Locale.ROOT);
            if (v.length() < 2) throw new ValidacionException("El apellido paterno requiere minimo 2 caracteres.");
            c.setApellidoPaterno(v);
        }
        if (req.getApellidoMaterno() != null && !req.getApellidoMaterno().isBlank()) {
            String v = req.getApellidoMaterno().trim().toUpperCase(Locale.ROOT);
            if (v.length() < 2) throw new ValidacionException("El apellido materno requiere minimo 2 caracteres.");
            c.setApellidoMaterno(v);
        }
        if (req.getCorreo() != null && !req.getCorreo().isBlank()) {
            String mail = req.getCorreo().trim().toLowerCase(Locale.ROOT);
            if (!TelefonoUtil.esCorreoSintaxisValida(mail) || TelefonoUtil.esCorreoPlaceholder(mail)) {
                throw new ValidacionException("Ingresa un correo electronico real y valido.");
            }
            if (!mail.equalsIgnoreCase(c.getCorreo()) && clienteRepo.existsByCorreo(mail)) {
                throw new CorreoDuplicadoException("El correo ya pertenece a otro usuario.");
            }
            c.setCorreo(mail);
        }
        if (req.getTelefonoMovil() != null && !req.getTelefonoMovil().isBlank()) {
            String tel = req.getTelefonoMovil().trim();
            if (!TelefonoUtil.esTelefonoMexicanoValido(tel)) {
                throw new ValidacionException(TelefonoUtil.diagnosticarTelefonoInvalido(tel));
            }
            c.setTelefonoMovil(tel);
        }
    }

    private void aplicarCambiosCredenciales(LoginCliente lg, AdminActualizarUsuarioRequestDTO req, boolean targetIsAdmin) {
        if (req.getUsuario() != null && !req.getUsuario().isBlank()) {
            String nuevoUsr = req.getUsuario().trim();
            if (!nuevoUsr.equalsIgnoreCase(lg.getUsuario())) {
                if (targetIsAdmin) {
                    throw new ValidacionException("No se puede renombrar la cuenta principal admin.");
                }
                if (!nuevoUsr.matches("^[a-zA-Z0-9._-]{4,50}$")) {
                    throw new ValidacionException("Formato de usuario invalido (4-50 caracteres).");
                }
                if (loginRepo.findByUsuario(nuevoUsr).isPresent()) {
                    throw new ValidacionException("El usuario '" + nuevoUsr + "' ya existe.");
                }
                authService.invalidarOtrasSesiones(lg.getUsuario(), null);
                lg.setUsuario(nuevoUsr);
            }
        }
        if (req.getNuevaContrasena() != null && !req.getNuevaContrasena().isBlank()) {
            String np = req.getNuevaContrasena();
            if (!np.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$")) {
                throw new ValidacionException("La contraseña requiere 8+ caracteres, mayuscula, minuscula y numero.");
            }
            lg.setContrasenaHash(passwordEncoder.encode(np));
            lg.setIntentosFallidos(0);
            lg.setBloqueado(false);
        }
        if (req.getBloqueado() != null) {
            if (targetIsAdmin && Boolean.TRUE.equals(req.getBloqueado())) {
                throw new ValidacionException("No se puede bloquear la cuenta admin.");
            }
            lg.setBloqueado(req.getBloqueado());
            if (!req.getBloqueado()) {
                lg.setIntentosFallidos(0);
            } else {
                authService.invalidarOtrasSesiones(lg.getUsuario(), null);
            }
        }
    }

    private void aplicarCambiosCuentaYSaldo(
            Cliente c,
            LoginCliente lg,
            AdminActualizarUsuarioRequestDTO req,
            boolean targetIsAdmin,
            String adminUser) {
        Cuenta cta = resolveCuenta(c);
        if (req.getActivo() != null) {
            if (targetIsAdmin && !req.getActivo()) {
                throw new ValidacionException("No se puede desactivar la cuenta principal admin.");
            }
            c.setActivo(req.getActivo());
            if (lg != null) {
                lg.setActivo(req.getActivo());
                if (!req.getActivo()) {
                    authService.invalidarOtrasSesiones(lg.getUsuario(), null);
                }
            }
            if (cta != null) {
                cta.setEstatus(req.getActivo()
                        ? EstatusCuentaEnum.ACTIVA.getCodigo()
                        : EstatusCuentaEnum.INACTIVA.getCodigo());
                cuentaRepo.save(cta);
            }
        }
        if (req.getSaldo() != null) {
            if (req.getSaldo().compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidacionException("El saldo no puede ser negativo.");
            }
            if (cta != null && cta.getId() != null) {
                Saldo s = saldoRepo.findByCuentaId(cta.getId()).orElseGet(() -> {
                    Saldo ns = new Saldo();
                    ns.setCuenta(cta);
                    return ns;
                });
                s.setSaldo(req.getSaldo().setScale(2, RoundingMode.HALF_UP));
                s.setFechaMovimiento(LocalDateTime.now());
                s.setConcepto("Ajuste admin por @" + adminUser);
                saldoRepo.save(s);
            }
        }
    }

    @Override
    @Transactional
    public GenericResponse eliminarUsuarioComoAdmin(String adminAutenticado, Long clienteId) {
        ownershipValidator.validarPrivilegiosAdmin(adminAutenticado);
        Cliente c = clienteRepo.findById(clienteId)
                .orElseThrow(() -> new ClienteNoEncontradoException("Usuario no encontrado con ID: " + clienteId));
        LoginCliente lg = resolveLogin(c);
        if (lg != null && AuthServiceImpl.esUsuarioAdmin(lg.getUsuario())) {
            throw new ValidacionException("No se puede eliminar la cuenta principal admin.");
        }

        String usr = lg != null ? lg.getUsuario() : ("ID-" + clienteId);
        if (lg != null && lg.getUsuario() != null) {
            authService.invalidarOtrasSesiones(lg.getUsuario(), null);
        }

        List<DatoBiometrico> bios = bioRepo.findByClienteId(clienteId);
        if (bios != null && !bios.isEmpty()) {
            bioRepo.deleteAll(bios);
        }

        Cuenta cta = resolveCuenta(c);
        if (cta != null && cta.getId() != null) {
            List<PagoServicio> pagos = pagoRepo.findByCuentaId(cta.getId());
            if (pagos != null && !pagos.isEmpty()) {
                pagoRepo.deleteAll(pagos);
            }
        }

        clienteRepo.delete(c);
        return new GenericResponse(0, "Usuario '" + usr + "' eliminado permanentemente.");
    }

    @Override
    @Transactional
    public GenericResponse eliminarTodosLosUsuariosComoAdmin(String adminAutenticado) {
        ownershipValidator.validarPrivilegiosAdmin(adminAutenticado);

        List<String> usuariosNoAdmin = clienteRepo.findNonAdminUsernamesRaw();
        if (usuariosNoAdmin != null) {
            for (String usr : usuariosNoAdmin) {
                if (usr != null && !usr.isBlank()) {
                    authService.invalidarOtrasSesiones(usr, null);
                }
            }
        }

        clienteRepo.deleteNonAdminPagosRaw();
        clienteRepo.deleteNonAdminSaldosRaw();
        clienteRepo.deleteNonAdminBiometriaRaw();
        clienteRepo.deleteNonAdminDomiciliosRaw();
        clienteRepo.deleteNonAdminCuentasRaw();
        clienteRepo.deleteNonAdminLoginsRaw();
        int eliminados = clienteRepo.deleteNonAdminClientesRaw();

        log.info("Eliminacion masiva ejecutada por @{}: {} clientes eliminados (cuenta admin conservada)",
                adminAutenticado, eliminados);

        if (eliminados == 0) {
            return new GenericResponse(0, "No habia usuarios clientes por eliminar (se conserva la cuenta @admin).");
        }
        return new GenericResponse(
                0,
                "Se eliminaron permanentemente " + eliminados + " usuarios clientes (se conserva la cuenta @admin).");
    }

    private LoginCliente resolveLogin(Cliente c) {
        if (c.getLoginCliente() != null) return c.getLoginCliente();
        return c.getId() != null ? loginRepo.findByClienteId(c.getId()).orElse(null) : null;
    }

    private Cuenta resolveCuenta(Cliente c) {
        if (c.getCuenta() != null) return c.getCuenta();
        return c.getId() != null ? cuentaRepo.findByClienteId(c.getId()).orElse(null) : null;
    }

    private AdminUsuarioDTO toDto(Cliente c) {
        LoginCliente lg = resolveLogin(c);
        Cuenta cta = resolveCuenta(c);
        BigDecimal monto = BigDecimal.ZERO;
        if (cta != null && cta.getId() != null) {
            monto = saldoRepo.findByCuentaId(cta.getId())
                    .map(Saldo::getSaldo)
                    .orElseGet(() -> cta.getSaldo() != null ? cta.getSaldo().getSaldo() : BigDecimal.ZERO);
        }
        boolean huella = c.getId() != null && bioRepo.findByClienteIdAndTipo(c.getId(), TIPO_HUELLA)
                .map(b -> Boolean.TRUE.equals(b.getActivo()))
                .orElse(false);
        String usr = lg != null ? lg.getUsuario() : null;
        boolean admin = AuthServiceImpl.esUsuarioAdmin(usr);

        StringBuilder full = new StringBuilder();
        if (c.getNombre() != null) full.append(c.getNombre().trim());
        if (c.getSegundoNombre() != null && !c.getSegundoNombre().isBlank()) full.append(" ").append(c.getSegundoNombre().trim());
        if (c.getApellidoPaterno() != null) full.append(" ").append(c.getApellidoPaterno().trim());
        if (c.getApellidoMaterno() != null) full.append(" ").append(c.getApellidoMaterno().trim());

        return AdminUsuarioDTO.builder()
                .clienteId(c.getId())
                .usuario(usr)
                .rol(admin ? "ADMIN" : "CLIENTE")
                .esAdmin(admin)
                .nombre(c.getNombre())
                .segundoNombre(c.getSegundoNombre())
                .apellidoPaterno(c.getApellidoPaterno())
                .apellidoMaterno(c.getApellidoMaterno())
                .nombreCompleto(full.toString().trim())
                .fechaNacimiento(c.getFechaNacimiento())
                .sexo(c.getSexo())
                .curp(c.getCurp())
                .rfc(c.getRfc())
                .correo(c.getCorreo())
                .telefonoMovil(c.getTelefonoMovil())
                .numeroCuenta(cta != null ? cta.getNumeroCuenta() : null)
                .estatusCuenta(cta != null ? cta.getEstatus() : "SIN_CUENTA")
                .saldo(monto)
                .activo(c.getActivo())
                .bloqueado(lg != null ? lg.getBloqueado() : false)
                .intentosFallidos(lg != null ? lg.getIntentosFallidos() : 0)
                .biometriaActiva(huella)
                .ultimoAcceso(lg != null ? lg.getUltimoAcceso() : null)
                .fechaRegistro(c.getFechaRegistro())
                .build();
    }
}
