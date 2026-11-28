package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.clientes.LoginCliente;
import com.proyecto.servicios.entity.clientes.Saldo;
import com.proyecto.servicios.exception.AutenticacionException;
import com.proyecto.servicios.model.auth.LoginRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;
import com.proyecto.servicios.repositorys.clientes.CuentaRepository;
import com.proyecto.servicios.repositorys.clientes.LoginClienteRepository;
import com.proyecto.servicios.repositorys.clientes.SaldoRepository;
import com.proyecto.servicios.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    public static final int MAX_INTENTOS_FALLIDOS = 5;
    public static final String MENSAJE_SESION_DESPLAZADA =
            "Se inició sesión con tu cuenta desde otro dispositivo. Por seguridad, esta sesión fue cerrada.";
    public static final String AVISO_SESION_PREVIA_CERRADA =
            "Se cerró automáticamente una sesión activa previa en otro dispositivo por seguridad.";
    private static final String REDIS_TOKEN_PREFIX = "auth:session:token:";
    private static final String REDIS_USER_PREFIX = "auth:session:user:";
    private static final String REDIS_DISPLACED_PREFIX = "auth:session:displaced:";
    private static final long DEFAULT_SESSION_TTL_SECONDS = 300L;
    private static final long MIN_DISPLACED_TTL_SECONDS = 600L;

    private final LoginClienteRepository loginClienteRepository;
    private final CuentaRepository cuentaRepository;
    private final SaldoRepository saldoRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final long sessionTtlSeconds;

    // Almacenamiento concurrente en memoria para sesiones activas con TTL deslizante
    private final Map<String, SessionEntry> tokenStore = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> usuarioToTokens = new ConcurrentHashMap<>();
    private final Map<String, Instant> displacedTokens = new ConcurrentHashMap<>();

    public AuthServiceImpl(
            LoginClienteRepository loginClienteRepository,
            CuentaRepository cuentaRepository,
            SaldoRepository saldoRepository,
            RedisTemplate<String, String> redisTemplate) {
        this(loginClienteRepository, cuentaRepository, saldoRepository, redisTemplate,
                new BCryptPasswordEncoder(), DEFAULT_SESSION_TTL_SECONDS);
    }

    @Autowired
    public AuthServiceImpl(
            LoginClienteRepository loginClienteRepository,
            CuentaRepository cuentaRepository,
            SaldoRepository saldoRepository,
            @Autowired(required = false) RedisTemplate<String, String> redisTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.session-ttl-seconds:300}") long sessionTtlSeconds) {
        this.loginClienteRepository = loginClienteRepository;
        this.cuentaRepository = cuentaRepository;
        this.saldoRepository = saldoRepository;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder != null ? passwordEncoder : new BCryptPasswordEncoder();
        this.sessionTtlSeconds = sessionTtlSeconds > 0 ? sessionTtlSeconds : DEFAULT_SESSION_TTL_SECONDS;
    }

    @Override
    @Transactional(noRollbackFor = AutenticacionException.class)
    public LoginResponseDTO login(LoginRequestDTO request) {
        if (request == null || request.getUsuario() == null || request.getContrasena() == null) {
            throw new AutenticacionException("Credenciales de acceso requeridas");
        }

        String username = request.getUsuario().trim();
        log.info("Procesando solicitud de autenticación para usuario: {}", username);

        LoginCliente login = loginClienteRepository.findByUsuario(username)
                .orElseThrow(() -> new AutenticacionException("Credenciales inválidas"));

        if (Boolean.TRUE.equals(login.getBloqueado())) {
            log.warn("Intento de acceso a cuenta bloqueada: {}", username);
            throw new AutenticacionException("El usuario se encuentra bloqueado por múltiples intentos fallidos de autenticación.");
        }

        if (!Boolean.TRUE.equals(login.getActivo())) {
            log.warn("Intento de acceso a usuario inactivo: {}", username);
            throw new AutenticacionException("El usuario se encuentra inactivo.");
        }

        String storedHash = login.getContrasenaHash();
        if (!verificarContrasena(request.getContrasena(), storedHash)) {
            int intentos = (login.getIntentosFallidos() == null ? 0 : login.getIntentosFallidos()) + 1;
            login.setIntentosFallidos(intentos);
            login.setUltimoIntentoFallido(LocalDateTime.now());

            if (intentos >= MAX_INTENTOS_FALLIDOS) {
                login.setBloqueado(true);
                loginClienteRepository.save(login);
                log.warn("Usuario {} bloqueado tras {} intentos fallidos consecutivos", username, intentos);
                throw new AutenticacionException("El usuario ha sido bloqueado tras alcanzar 5 intentos fallidos.");
            }

            loginClienteRepository.save(login);
            log.warn("Fallo de contraseña para usuario {}", username);
            throw new AutenticacionException("Credenciales inválidas");
        }

        // Migración transparente de hash legado SHA-256 hacia BCrypt al iniciar sesión correctamente
        if (!esHashBCrypt(storedHash)) {
            login.setContrasenaHash(passwordEncoder.encode(request.getContrasena()));
        }

        // Login exitoso: restablecer contador de fallos, registrar sesión única en BD y actualizar último acceso
        String token = UUID.randomUUID().toString().replace("-", "");
        boolean sesionPreviaBd = actualizarSesionUnicaEnEntidad(login, token);
        login.setIntentosFallidos(0);
        login.setUltimoAcceso(LocalDateTime.now());
        loginClienteRepository.save(login);

        Cliente cliente = login.getCliente();
        if (cliente == null || !Boolean.TRUE.equals(cliente.getActivo())) {
            throw new AutenticacionException("El cliente asociado no se encuentra activo.");
        }

        Cuenta cuenta = obtenerCuentaCliente(cliente);
        BigDecimal saldo = obtenerSaldoCuenta(cuenta);

        boolean sesionPreviaMemoria = registrarSesionUnica(token, username);
        boolean sesionPreviaCerrada = sesionPreviaBd || sesionPreviaMemoria;

        log.info("Autenticación exitosa para usuario: {} con clienteId: {}", username, cliente.getId());
        boolean isAdmin = esUsuarioAdmin(username);

        return LoginResponseDTO.builder()
                .token(token)
                .usuario(username)
                .clienteId(cliente.getId())
                .nombreCompleto(construirNombreCompleto(cliente))
                .email(cliente.getCorreo())
                .telefonoMovil(cliente.getTelefonoMovil())
                .numeroCuenta(cuenta != null ? cuenta.getNumeroCuenta() : null)
                .saldo(saldo)
                .ultimoAcceso(login.getUltimoAcceso())
                .esAdmin(isAdmin)
                .rol(isAdmin ? "ADMIN" : "CLIENTE")
                .sesionPreviaCerrada(sesionPreviaCerrada)
                .avisoSesion(sesionPreviaCerrada ? AVISO_SESION_PREVIA_CERRADA : null)
                .profesion(cliente.getProfesion())
                .totpHabilitado(Boolean.TRUE.equals(login.getTotpHabilitado()))
                .build();
    }

    @Override
    @Transactional(noRollbackFor = AutenticacionException.class)
    public LoginResponseDTO crearSesionParaUsuario(String usuario) {
        if (usuario == null || usuario.isBlank()) {
            throw new AutenticacionException("El nombre de usuario es obligatorio.");
        }
        String username = usuario.trim();
        LoginCliente login = loginClienteRepository.findByUsuario(username)
                .orElseThrow(() -> new AutenticacionException("Usuario no encontrado."));

        if (Boolean.TRUE.equals(login.getBloqueado())) {
            throw new AutenticacionException("El usuario se encuentra bloqueado.");
        }
        if (!Boolean.TRUE.equals(login.getActivo())) {
            throw new AutenticacionException("El usuario se encuentra inactivo.");
        }

        Cliente cliente = login.getCliente();
        if (cliente == null || !Boolean.TRUE.equals(cliente.getActivo())) {
            throw new AutenticacionException("El cliente asociado no se encuentra activo.");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        boolean sesionPreviaBd = actualizarSesionUnicaEnEntidad(login, token);
        login.setIntentosFallidos(0);
        login.setUltimoAcceso(LocalDateTime.now());
        loginClienteRepository.save(login);

        Cuenta cuenta = obtenerCuentaCliente(cliente);
        BigDecimal saldo = obtenerSaldoCuenta(cuenta);

        boolean sesionPreviaMemoria = registrarSesionUnica(token, username);
        boolean sesionPreviaCerrada = sesionPreviaBd || sesionPreviaMemoria;

        log.info("Sesión emitida tras verificación alterna para usuario={} clienteId={}", username, cliente.getId());
        boolean isAdmin = esUsuarioAdmin(username);

        return LoginResponseDTO.builder()
                .token(token)
                .usuario(username)
                .clienteId(cliente.getId())
                .nombreCompleto(construirNombreCompleto(cliente))
                .email(cliente.getCorreo())
                .telefonoMovil(cliente.getTelefonoMovil())
                .numeroCuenta(cuenta != null ? cuenta.getNumeroCuenta() : null)
                .saldo(saldo)
                .ultimoAcceso(login.getUltimoAcceso())
                .esAdmin(isAdmin)
                .rol(isAdmin ? "ADMIN" : "CLIENTE")
                .sesionPreviaCerrada(sesionPreviaCerrada)
                .avisoSesion(sesionPreviaCerrada ? AVISO_SESION_PREVIA_CERRADA : null)
                .profesion(cliente.getProfesion())
                .totpHabilitado(Boolean.TRUE.equals(login.getTotpHabilitado()))
                .build();
    }

    @Override
    public void logout(String token, String usuarioBody) {
        String tokenLimpio = limpiarToken(token);
        if (tokenLimpio == null) {
            throw new AutenticacionException("Sesión no autorizada o expirada");
        }

        String tokenOwner = resolverPropietarioTokenSinRefrescar(tokenLimpio);
        if (tokenOwner == null) {
            throw new AutenticacionException("Sesión no autorizada o expirada");
        }

        if (usuarioBody != null && !usuarioBody.isBlank() && !usuarioBody.trim().equals(tokenOwner)) {
            throw new AccessDeniedException("No puedes cerrar la sesión de otro usuario.");
        }

        removerTokenDeAlmacen(tokenLimpio, tokenOwner);
        loginClienteRepository.findByUsuario(tokenOwner).ifPresent(lg -> {
            if (tokenLimpio.equals(lg.getTokenSesionActiva())) {
                lg.setTokenSesionActiva(null);
                lg.setSesionExpiraEn(null);
                loginClienteRepository.save(lg);
            }
        });
        log.info("Sesión cerrada exitosamente para usuario: {}", tokenOwner);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileDTO obtenerPerfilAutenticado(String token, String usuarioIgnorado) {
        String tokenLimpio = limpiarToken(token);
        if (tokenLimpio == null) {
            throw new AutenticacionException("Sesión no autorizada o expirada");
        }

        String username = validarTokenYObtenerUsuario(tokenLimpio)
                .orElseThrow(() -> new AutenticacionException(
                        fueDesplazadoPorOtroDispositivo(tokenLimpio)
                                ? MENSAJE_SESION_DESPLAZADA
                                : "Sesión no autorizada o expirada"));

        LoginCliente login = loginClienteRepository.findByUsuario(username.trim())
                .orElseThrow(() -> new AutenticacionException("Usuario no encontrado"));

        if (Boolean.TRUE.equals(login.getBloqueado())) {
            throw new AutenticacionException("El usuario se encuentra bloqueado.");
        }

        if (!Boolean.TRUE.equals(login.getActivo())) {
            throw new AutenticacionException("El usuario se encuentra inactivo.");
        }

        Cliente cliente = login.getCliente();
        if (cliente == null || !Boolean.TRUE.equals(cliente.getActivo())) {
            throw new AutenticacionException("El cliente asociado no se encuentra activo.");
        }

        Cuenta cuenta = obtenerCuentaCliente(cliente);
        BigDecimal saldo = obtenerSaldoCuenta(cuenta);
        boolean isAdmin = esUsuarioAdmin(login.getUsuario());
        var dom = cliente.getDomicilio();

        return UserProfileDTO.builder()
                .clienteId(cliente.getId())
                .usuario(login.getUsuario())
                .nombre(cliente.getNombre())
                .segundoNombre(cliente.getSegundoNombre())
                .apellidoPaterno(cliente.getApellidoPaterno())
                .apellidoMaterno(cliente.getApellidoMaterno())
                .nombreCompleto(construirNombreCompleto(cliente))
                .email(cliente.getCorreo())
                .telefonoMovil(cliente.getTelefonoMovil())
                .telefonoAlternativo(cliente.getTelefonoAlternativo())
                .curp(cliente.getCurp())
                .rfc(cliente.getRfc())
                .fechaNacimiento(cliente.getFechaNacimiento() != null ? cliente.getFechaNacimiento().toString() : null)
                .sexo(cliente.getSexo())
                .nacionalidad(cliente.getNacionalidad())
                .estadoCivil(cliente.getEstadoCivil())
                .ocupacion(cliente.getOcupacion())
                .profesion(cliente.getProfesion())
                .empresa(cliente.getEmpresa())
                .ingresoMensual(cliente.getIngresoMensual())
                .calle(dom != null ? dom.getCalle() : null)
                .numeroExterior(dom != null ? dom.getNumeroExterior() : null)
                .numeroInterior(dom != null ? dom.getNumeroInterior() : null)
                .colonia(dom != null ? dom.getColonia() : null)
                .municipio(dom != null ? dom.getMunicipio() : null)
                .estado(dom != null ? dom.getEstado() : null)
                .codigoPostal(dom != null ? dom.getCodigoPostal() : null)
                .pais(dom != null ? dom.getPais() : null)
                .numeroCuenta(cuenta != null ? cuenta.getNumeroCuenta() : null)
                .saldo(saldo)
                .activo(cliente.getActivo())
                .ultimoAcceso(login.getUltimoAcceso())
                .esAdmin(isAdmin)
                .rol(isAdmin ? "ADMIN" : "CLIENTE")
                .totpHabilitado(Boolean.TRUE.equals(login.getTotpHabilitado()))
                .build();
    }

    public static boolean esUsuarioAdmin(String usuario) {
        return usuario != null && "admin".equalsIgnoreCase(usuario.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> validarTokenYObtenerUsuario(String token) {
        String tokenLimpio = limpiarToken(token);
        if (tokenLimpio == null) {
            return Optional.empty();
        }

        String username = null;
        SessionEntry entry = tokenStore.get(tokenLimpio);
        Instant now = Instant.now();

        if (entry != null) {
            if (now.isAfter(entry.expiresAt())) {
                removerTokenDeAlmacen(tokenLimpio, entry.usuario());
                return Optional.empty();
            }
            username = entry.usuario();
            tokenStore.put(tokenLimpio, new SessionEntry(username, now.plusSeconds(sessionTtlSeconds)));
        }

        if (username == null && redisTemplate != null) {
            try {
                username = redisTemplate.opsForValue().get(REDIS_TOKEN_PREFIX + tokenLimpio);
                if (username != null && !username.isBlank()) {
                    redisTemplate.expire(REDIS_TOKEN_PREFIX + tokenLimpio, sessionTtlSeconds, TimeUnit.SECONDS);
                    tokenStore.put(tokenLimpio, new SessionEntry(username, now.plusSeconds(sessionTtlSeconds)));
                    usuarioToTokens.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(tokenLimpio);
                }
            } catch (Exception e) {
                log.warn("Error consultando token en Redis: {}", e.getMessage());
            }
        }

        if (username == null || username.isBlank()) {
            Optional<LoginCliente> porTokenOpt = loginClienteRepository.findByTokenSesionActiva(tokenLimpio);
            if (porTokenOpt.isPresent()) {
                LoginCliente lgPorToken = porTokenOpt.get();
                if (lgPorToken.getSesionExpiraEn() == null
                        || LocalDateTime.now().isBefore(lgPorToken.getSesionExpiraEn())) {
                    username = lgPorToken.getUsuario();
                    tokenStore.put(tokenLimpio, new SessionEntry(username, now.plusSeconds(sessionTtlSeconds)));
                    usuarioToTokens.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(tokenLimpio);
                }
            }
        }

        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        Optional<LoginCliente> loginOpt = loginClienteRepository.findByUsuario(username.trim());
        if (loginOpt.isEmpty()) {
            return Optional.empty();
        }
        LoginCliente login = loginOpt.get();
        if (login.getTokenSesionActiva() != null && !login.getTokenSesionActiva().equals(tokenLimpio)) {
            removerTokenDeAlmacen(tokenLimpio, login.getUsuario());
            marcarTokenDesplazado(tokenLimpio, now);
            return Optional.empty();
        }
        if (tokenLimpio.equals(login.getTokenSesionActiva())
                && login.getSesionExpiraEn() != null
                && LocalDateTime.now().plusSeconds(Math.max(10L, sessionTtlSeconds - 45L)).isAfter(login.getSesionExpiraEn())) {
            login.setSesionExpiraEn(LocalDateTime.now().plusSeconds(sessionTtlSeconds));
            loginClienteRepository.save(login);
        }
        if (!Boolean.TRUE.equals(login.getActivo()) || Boolean.TRUE.equals(login.getBloqueado())) {
            return Optional.empty();
        }
        Cliente cliente = login.getCliente();
        if (cliente == null || !Boolean.TRUE.equals(cliente.getActivo())) {
            return Optional.empty();
        }
        return Optional.of(login.getUsuario());
    }

    @Override
    public boolean verificarContrasena(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (esHashBCrypt(storedHash)) {
            return passwordEncoder.matches(rawPassword, storedHash);
        }
        String sha = cifrarSHA256(rawPassword);
        if (sha == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                storedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String codificarContrasena(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public void invalidarOtrasSesiones(String usuario, String tokenAConservar) {
        if (usuario == null || usuario.isBlank()) {
            return;
        }
        String userKey = usuario.trim();
        String tokenConservado = limpiarToken(tokenAConservar);

        Set<String> tokens = usuarioToTokens.get(userKey);
        if (tokens != null) {
            List<String> copia = new ArrayList<>(tokens);
            for (String t : copia) {
                if (tokenConservado == null || !tokenConservado.equals(t)) {
                    tokenStore.remove(t);
                    tokens.remove(t);
                    eliminarTokenRedis(t, userKey);
                }
            }
            if (tokens.isEmpty()) {
                usuarioToTokens.remove(userKey, tokens);
            }
        }

        if (redisTemplate != null) {
            try {
                Set<String> redisTokens = redisTemplate.opsForSet().members(REDIS_USER_PREFIX + userKey);
                if (redisTokens != null) {
                    for (String t : redisTokens) {
                        if (tokenConservado == null || !tokenConservado.equals(t)) {
                            redisTemplate.delete(REDIS_TOKEN_PREFIX + t);
                            redisTemplate.opsForSet().remove(REDIS_USER_PREFIX + userKey, t);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error invalidando otras sesiones en Redis: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean fueDesplazadoPorOtroDispositivo(String token) {
        String tokenLimpio = limpiarToken(token);
        if (tokenLimpio == null) {
            return false;
        }
        Instant now = Instant.now();
        Instant until = displacedTokens.get(tokenLimpio);
        if (until != null) {
            if (now.isAfter(until)) {
                displacedTokens.remove(tokenLimpio, until);
                return false;
            }
            return true;
        }
        if (redisTemplate != null) {
            try {
                var valOps = redisTemplate.opsForValue();
                if (valOps != null && valOps.get(REDIS_DISPLACED_PREFIX + tokenLimpio) != null) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Error consultando desplazamiento en Redis: {}", e.getMessage());
            }
        }
        return loginClienteRepository.findByTokenSesionDesplazada(tokenLimpio).isPresent();
    }

    @Override
    public boolean verificarSesionActivaSinRefrescar(String token) {
        String tokenLimpio = limpiarToken(token);
        if (tokenLimpio == null) {
            throw new AutenticacionException("Sesión no autorizada o expirada");
        }
        if (fueDesplazadoPorOtroDispositivo(tokenLimpio)) {
            throw new AutenticacionException(MENSAJE_SESION_DESPLAZADA);
        }
        String owner = resolverPropietarioTokenSinRefrescar(tokenLimpio);
        if (owner == null || owner.isBlank()) {
            Optional<LoginCliente> porToken = loginClienteRepository.findByTokenSesionActiva(tokenLimpio);
            if (porToken.isPresent()
                    && (porToken.get().getSesionExpiraEn() == null
                            || LocalDateTime.now().isBefore(porToken.get().getSesionExpiraEn()))) {
                owner = porToken.get().getUsuario();
            }
        }
        if (owner == null || owner.isBlank()) {
            throw new AutenticacionException("Sesión no autorizada o expirada");
        }
        Optional<LoginCliente> loginOpt = loginClienteRepository.findByUsuario(owner.trim());
        if (loginOpt.isPresent()) {
            LoginCliente lg = loginOpt.get();
            if (lg.getTokenSesionActiva() != null && !lg.getTokenSesionActiva().equals(tokenLimpio)) {
                removerTokenDeAlmacen(tokenLimpio, owner);
                marcarTokenDesplazado(tokenLimpio, Instant.now());
                throw new AutenticacionException(MENSAJE_SESION_DESPLAZADA);
            }
        }
        return true;
    }

    private boolean actualizarSesionUnicaEnEntidad(LoginCliente login, String nuevoToken) {
        if (login == null) {
            return false;
        }
        boolean desplazada = false;
        String tokenPrevio = login.getTokenSesionActiva();
        if (tokenPrevio != null && !tokenPrevio.isBlank() && !tokenPrevio.equals(nuevoToken)) {
            if (login.getSesionExpiraEn() == null || LocalDateTime.now().isBefore(login.getSesionExpiraEn())) {
                desplazada = true;
            }
            login.setTokenSesionDesplazada(tokenPrevio);
            marcarTokenDesplazado(tokenPrevio, Instant.now());
        }
        login.setTokenSesionActiva(nuevoToken);
        login.setSesionExpiraEn(LocalDateTime.now().plusSeconds(sessionTtlSeconds));
        return desplazada;
    }

    @Scheduled(fixedDelay = 60000)
    public void evictExpiredSessions() {
        Instant now = Instant.now();
        List<Map.Entry<String, SessionEntry>> snapshot = new ArrayList<>(tokenStore.entrySet());
        for (Map.Entry<String, SessionEntry> entry : snapshot) {
            if (now.isAfter(entry.getValue().expiresAt())) {
                String t = entry.getKey();
                String u = entry.getValue().usuario();
                if (tokenStore.remove(t, entry.getValue())) {
                    Set<String> userTokens = usuarioToTokens.get(u);
                    if (userTokens != null) {
                        userTokens.remove(t);
                        if (userTokens.isEmpty()) {
                            usuarioToTokens.remove(u, userTokens);
                        }
                    }
                }
            }
        }
        displacedTokens.entrySet().removeIf(e -> now.isAfter(e.getValue()));
    }

    private boolean esHashBCrypt(String hash) {
        return hash != null && (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
    }

    private String resolverPropietarioTokenSinRefrescar(String tokenLimpio) {
        SessionEntry entry = tokenStore.get(tokenLimpio);
        if (entry != null) {
            if (Instant.now().isAfter(entry.expiresAt())) {
                removerTokenDeAlmacen(tokenLimpio, entry.usuario());
                return null;
            }
            return entry.usuario();
        }
        if (redisTemplate != null) {
            try {
                String owner = redisTemplate.opsForValue().get(REDIS_TOKEN_PREFIX + tokenLimpio);
                if (owner != null && !owner.isBlank()) {
                    return owner;
                }
            } catch (Exception e) {
                log.warn("Error consultando propietario de token en Redis: {}", e.getMessage());
            }
        }
        return null;
    }

    private void removerTokenDeAlmacen(String tokenLimpio, String owner) {
        tokenStore.remove(tokenLimpio);
        if (owner != null) {
            Set<String> tokens = usuarioToTokens.get(owner);
            if (tokens != null) {
                tokens.remove(tokenLimpio);
                if (tokens.isEmpty()) {
                    usuarioToTokens.remove(owner, tokens);
                }
            }
        }
        eliminarTokenRedis(tokenLimpio, owner);
    }

    private void eliminarTokenRedis(String tokenLimpio, String owner) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(REDIS_TOKEN_PREFIX + tokenLimpio);
                if (owner != null) {
                    redisTemplate.opsForSet().remove(REDIS_USER_PREFIX + owner, tokenLimpio);
                }
            } catch (Exception e) {
                log.warn("Error invalidando token en Redis: {}", e.getMessage());
            }
        }
    }

    private boolean registrarSesionUnica(String token, String usuario) {
        Instant now = Instant.now();
        String userKey = usuario.trim();
        boolean hadActivePrevious = false;

        Set<String> existingTokens = usuarioToTokens.get(userKey);
        if (existingTokens != null) {
            List<String> copia = new ArrayList<>(existingTokens);
            for (String prevToken : copia) {
                if (!token.equals(prevToken)) {
                    SessionEntry prevEntry = tokenStore.remove(prevToken);
                    existingTokens.remove(prevToken);
                    eliminarTokenRedis(prevToken, userKey);
                    if (prevEntry != null && !now.isAfter(prevEntry.expiresAt())) {
                        hadActivePrevious = true;
                        marcarTokenDesplazado(prevToken, now);
                    }
                }
            }
            if (existingTokens.isEmpty()) {
                usuarioToTokens.remove(userKey, existingTokens);
            }
        }

        if (redisTemplate != null) {
            try {
                var setOps = redisTemplate.opsForSet();
                if (setOps != null) {
                    Set<String> redisTokens = setOps.members(REDIS_USER_PREFIX + userKey);
                    if (redisTokens != null) {
                        var valOps = redisTemplate.opsForValue();
                        for (String prevToken : redisTokens) {
                            if (!token.equals(prevToken)) {
                                boolean wasActiveInRedis = valOps != null
                                        && valOps.get(REDIS_TOKEN_PREFIX + prevToken) != null;
                                redisTemplate.delete(REDIS_TOKEN_PREFIX + prevToken);
                                setOps.remove(REDIS_USER_PREFIX + userKey, prevToken);
                                if (wasActiveInRedis) {
                                    hadActivePrevious = true;
                                    marcarTokenDesplazado(prevToken, now);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error revocando sesiones previas en Redis: {}", e.getMessage());
            }
        }

        if (hadActivePrevious) {
            log.warn("Sesión previa cerrada por inicio de sesión en otro dispositivo para usuario: {}", userKey);
        }

        registrarSesion(token, userKey);
        return hadActivePrevious;
    }

    private void marcarTokenDesplazado(String oldToken, Instant now) {
        long displacedTtl = Math.max(sessionTtlSeconds, MIN_DISPLACED_TTL_SECONDS);
        displacedTokens.put(oldToken, now.plusSeconds(displacedTtl));
        if (redisTemplate != null) {
            try {
                var valOps = redisTemplate.opsForValue();
                if (valOps != null) {
                    valOps.set(REDIS_DISPLACED_PREFIX + oldToken, "1", displacedTtl, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                log.warn("No se pudo registrar sesión desplazada en Redis: {}", e.getMessage());
            }
        }
    }

    private void registrarSesion(String token, String usuario) {
        Instant expiresAt = Instant.now().plusSeconds(sessionTtlSeconds);
        tokenStore.put(token, new SessionEntry(usuario, expiresAt));
        usuarioToTokens.computeIfAbsent(usuario, k -> ConcurrentHashMap.newKeySet()).add(token);

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(REDIS_TOKEN_PREFIX + token, usuario, sessionTtlSeconds, TimeUnit.SECONDS);
                redisTemplate.opsForSet().add(REDIS_USER_PREFIX + usuario, token);
                redisTemplate.expire(REDIS_USER_PREFIX + usuario, sessionTtlSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("No se pudo persistir la sesión en Redis (usando memoria interna): {}", e.getMessage());
            }
        }
    }

    private Cuenta obtenerCuentaCliente(Cliente cliente) {
        if (cliente == null) {
            return null;
        }
        Optional<Cuenta> cuentaOpt = cuentaRepository.findByClienteId(cliente.getId());
        return cuentaOpt.orElse(cliente.getCuenta());
    }

    private BigDecimal obtenerSaldoCuenta(Cuenta cuenta) {
        if (cuenta == null || cuenta.getId() == null) {
            return BigDecimal.ZERO;
        }
        return saldoRepository.findByCuentaId(cuenta.getId())
                .map(Saldo::getSaldo)
                .orElse(BigDecimal.ZERO);
    }

    private String construirNombreCompleto(Cliente c) {
        if (c == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (c.getNombre() != null) {
            sb.append(c.getNombre().trim());
        }
        if (c.getSegundoNombre() != null && !c.getSegundoNombre().isBlank()) {
            sb.append(" ").append(c.getSegundoNombre().trim());
        }
        if (c.getApellidoPaterno() != null) {
            sb.append(" ").append(c.getApellidoPaterno().trim());
        }
        if (c.getApellidoMaterno() != null) {
            sb.append(" ").append(c.getApellidoMaterno().trim());
        }
        return sb.toString().trim();
    }

    private String limpiarToken(String token) {
        if (token == null) {
            return null;
        }
        String t = token.trim();
        if (t.length() >= 7 && t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            t = t.substring(7).trim();
        }
        return t.isEmpty() ? null : t;
    }

    public static String cifrarSHA256(String input) {
        if (input == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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
            throw new RuntimeException("Error cifrando contraseña con SHA-256", e);
        }
    }

    private record SessionEntry(String usuario, Instant expiresAt) {
    }
}
