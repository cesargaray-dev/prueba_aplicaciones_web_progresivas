package com.proyecto.servicios.entity.clientes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_clientes")
@Getter
@Setter
public class LoginCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Usuario cifrado con BCrypt/SHA-256 */
    @Column(name = "usuario", nullable = false, unique = true, columnDefinition = "TEXT")
    private String usuario;

    /** Hash de contraseña (BCrypt) */
    @Column(name = "contrasena_hash", nullable = false, columnDefinition = "TEXT")
    private String contrasenaHash;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @Column(name = "ultimo_intento_fallido")
    private LocalDateTime ultimoIntentoFallido;

    /** Contador de intentos fallidos (se reinicia con login exitoso) */
    @Column(name = "intentos_fallidos", nullable = false)
    private Integer intentosFallidos = 0;

    /** True si se bloqueó por inactividad (5 min) o demasiados intentos */
    @Column(name = "bloqueado", nullable = false)
    private Boolean bloqueado = false;

    @Column(name = "token_sesion_activa", columnDefinition = "TEXT")
    private String tokenSesionActiva;

    @Column(name = "token_sesion_desplazada", columnDefinition = "TEXT")
    private String tokenSesionDesplazada;

    @Column(name = "sesion_expira_en")
    private LocalDateTime sesionExpiraEn;

    @Column(name = "totp_secret", columnDefinition = "TEXT")
    private String totpSecret;

    @Column(name = "totp_habilitado", nullable = false)
    private Boolean totpHabilitado = false;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    void onCreate() {
        fechaRegistro = LocalDateTime.now();
        fechaActualizacion = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        fechaActualizacion = LocalDateTime.now();
    }
}
