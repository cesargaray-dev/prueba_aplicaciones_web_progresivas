package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.DatoBiometrico;
import com.proyecto.servicios.entity.clientes.LoginCliente;
import com.proyecto.servicios.exception.AutenticacionException;
import com.proyecto.servicios.exception.ValidacionException;
import com.proyecto.servicios.model.auth.BiometriaEstadoResponseDTO;
import com.proyecto.servicios.model.auth.BiometriaLoginRequestDTO;
import com.proyecto.servicios.model.auth.BiometriaRegistroRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;
import com.proyecto.servicios.repositorys.clientes.ClienteRepository;
import com.proyecto.servicios.repositorys.clientes.DatoBiometricoRepository;
import com.proyecto.servicios.repositorys.clientes.LoginClienteRepository;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.BiometriaAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class BiometriaAuthServiceImpl implements BiometriaAuthService {

    public static final String TIPO_HUELLA = "HUELLA";
    private static final String HASH_REVOCADO = "REVOCADO";

    private final AuthService authService;
    private final ClienteRepository clienteRepository;
    private final LoginClienteRepository loginClienteRepository;
    private final DatoBiometricoRepository datoBiometricoRepository;

    public BiometriaAuthServiceImpl(
            AuthService authService,
            ClienteRepository clienteRepository,
            LoginClienteRepository loginClienteRepository,
            DatoBiometricoRepository datoBiometricoRepository) {
        this.authService = authService;
        this.clienteRepository = clienteRepository;
        this.loginClienteRepository = loginClienteRepository;
        this.datoBiometricoRepository = datoBiometricoRepository;
    }

    @Override
    @Transactional
    public BiometriaEstadoResponseDTO registrarHuella(
            String authHeader, String usuarioParam, BiometriaRegistroRequestDTO request) {
        if (request == null || request.getTokenBiometrico() == null
                || request.getTokenBiometrico().trim().length() < 32) {
            throw new ValidacionException(
                    "El token biométrico del dispositivo debe tener al menos 32 caracteres.");
        }

        UserProfileDTO perfil = authService.obtenerPerfilAutenticado(authHeader, usuarioParam);
        Cliente cliente = clienteRepository.findById(perfil.getClienteId())
                .orElseThrow(() -> new ValidacionException("No se encontró el perfil del cliente autenticado."));

        String hash = AuthServiceImpl.cifrarSHA256(request.getTokenBiometrico().trim());
        if (hash == null) {
            throw new ValidacionException("No fue posible procesar el token biométrico.");
        }

        DatoBiometrico dato = datoBiometricoRepository
                .findByClienteIdAndTipo(cliente.getId(), TIPO_HUELLA)
                .orElseGet(() -> {
                    DatoBiometrico nuevo = new DatoBiometrico();
                    nuevo.setCliente(cliente);
                    nuevo.setTipo(TIPO_HUELLA);
                    return nuevo;
                });

        dato.setHashBiometrico(hash);
        dato.setActivo(true);
        datoBiometricoRepository.save(dato);

        log.info("Acceso biométrico (HUELLA) registrado/actualizado para usuario={} clienteId={}",
                perfil.getUsuario(), cliente.getId());

        return BiometriaEstadoResponseDTO.builder()
                .habilitada(true)
                .usuario(perfil.getUsuario())
                .tipo(TIPO_HUELLA)
                .mensaje("Acceso con huella digital activado. Ahora puedes elegir entrar con huella o contraseña.")
                .build();
    }

    @Override
    @Transactional
    public BiometriaEstadoResponseDTO desactivarHuella(String authHeader, String usuarioParam) {
        UserProfileDTO perfil = authService.obtenerPerfilAutenticado(authHeader, usuarioParam);
        Optional<DatoBiometrico> datoOpt = datoBiometricoRepository
                .findByClienteIdAndTipo(perfil.getClienteId(), TIPO_HUELLA);

        if (datoOpt.isPresent()) {
            DatoBiometrico dato = datoOpt.get();
            dato.setActivo(false);
            dato.setHashBiometrico(HASH_REVOCADO);
            datoBiometricoRepository.save(dato);
            log.info("Acceso biométrico (HUELLA) revocado para usuario={}", perfil.getUsuario());
        }

        return BiometriaEstadoResponseDTO.builder()
                .habilitada(false)
                .usuario(perfil.getUsuario())
                .tipo(TIPO_HUELLA)
                .mensaje("Acceso con huella digital desactivado. Tu cuenta solo aceptará inicio con contraseña.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BiometriaEstadoResponseDTO consultarEstado(String authHeader, String usuarioParam) {
        UserProfileDTO perfil = authService.obtenerPerfilAutenticado(authHeader, usuarioParam);
        boolean activa = datoBiometricoRepository
                .findByClienteIdAndTipo(perfil.getClienteId(), TIPO_HUELLA)
                .map(d -> Boolean.TRUE.equals(d.getActivo()) && !HASH_REVOCADO.equals(d.getHashBiometrico()))
                .orElse(false);

        return BiometriaEstadoResponseDTO.builder()
                .habilitada(activa)
                .usuario(perfil.getUsuario())
                .tipo(TIPO_HUELLA)
                .mensaje(activa
                        ? "Huella digital activa para este usuario."
                        : "Huella digital no configurada.")
                .build();
    }

    @Override
    @Transactional(noRollbackFor = AutenticacionException.class)
    public LoginResponseDTO loginConHuella(BiometriaLoginRequestDTO request) {
        if (request == null || request.getUsuario() == null || request.getUsuario().isBlank()
                || request.getTokenBiometrico() == null || request.getTokenBiometrico().isBlank()) {
            throw new AutenticacionException("Credenciales biométricas incompletas.");
        }

        String username = request.getUsuario().trim();
        LoginCliente login = loginClienteRepository.findByUsuario(username)
                .orElseThrow(() -> new AutenticacionException(
                        "Usuario no encontrado o sin acceso biométrico configurado."));

        if (Boolean.TRUE.equals(login.getBloqueado())) {
            throw new AutenticacionException(
                    "El usuario se encuentra bloqueado. Contacta soporte o desbloquea tu acceso.");
        }
        if (!Boolean.TRUE.equals(login.getActivo())) {
            throw new AutenticacionException("El usuario se encuentra inactivo.");
        }

        Cliente cliente = login.getCliente();
        if (cliente == null || !Boolean.TRUE.equals(cliente.getActivo())) {
            throw new AutenticacionException("El cliente asociado no se encuentra activo.");
        }

        DatoBiometrico dato = datoBiometricoRepository
                .findByClienteIdAndTipo(cliente.getId(), TIPO_HUELLA)
                .filter(d -> Boolean.TRUE.equals(d.getActivo()))
                .orElseThrow(() -> new AutenticacionException(
                        "El acceso con huella digital no está activado para esta cuenta. Inicia sesión con tu contraseña."));

        String hashEntrante = AuthServiceImpl.cifrarSHA256(request.getTokenBiometrico().trim());
        String hashGuardado = dato.getHashBiometrico();

        if (hashEntrante == null || hashGuardado == null || HASH_REVOCADO.equals(hashGuardado)
                || !MessageDigest.isEqual(
                        hashEntrante.toLowerCase().getBytes(StandardCharsets.UTF_8),
                        hashGuardado.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            int intentos = (login.getIntentosFallidos() == null ? 0 : login.getIntentosFallidos()) + 1;
            login.setIntentosFallidos(intentos);
            login.setUltimoIntentoFallido(LocalDateTime.now());
            if (intentos >= AuthServiceImpl.MAX_INTENTOS_FALLIDOS) {
                login.setBloqueado(true);
                loginClienteRepository.save(login);
                log.warn("Usuario {} bloqueado tras {} intentos biométricos fallidos consecutivos", username, intentos);
                throw new AutenticacionException(
                        "El usuario ha sido bloqueado tras alcanzar 5 intentos fallidos de acceso biométrico.");
            }
            loginClienteRepository.save(login);
            log.warn("Intento biométrico rechazado para usuario={}", username);
            throw new AutenticacionException(
                    "La huella vinculada en este dispositivo expiró o fue revocada. Ingresa con tu contraseña y vuelve a activarla.");
        }

        log.info("Autenticación biométrica (HUELLA) exitosa para usuario={}", username);
        return authService.crearSesionParaUsuario(username);
    }
}
