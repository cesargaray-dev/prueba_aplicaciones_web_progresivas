package com.proyecto.servicios.repositorys.clientes;

import com.proyecto.servicios.entity.clientes.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    @Query(value = """
            SELECT
                c.id,
                l.usuario,
                c.nombre,
                c.segundo_nombre,
                c.apellido_paterno,
                c.apellido_materno,
                c.fecha_nacimiento,
                c.sexo,
                c.curp,
                c.rfc,
                c.correo,
                c.telefono_movil,
                ct.numero_cuenta,
                ct.estatus,
                COALESCE(s.saldo, 0),
                c.activo,
                COALESCE(l.bloqueado, false),
                COALESCE(l.intentos_fallidos, 0),
                EXISTS (
                    SELECT 1 FROM datos_biometricos db
                    WHERE db.cliente_id = c.id
                      AND db.tipo = 'HUELLA_DISPOSITIVO'
                      AND db.activo = true
                ) AS biometria_activa,
                l.ultimo_acceso,
                c.fecha_registro
            FROM clientes c
            LEFT JOIN login_clientes l ON l.cliente_id = c.id
            LEFT JOIN cuentas ct ON ct.cliente_id = c.id
            LEFT JOIN saldos s ON s.cuenta_id = ct.id
            ORDER BY c.id DESC
            """, nativeQuery = true)
    List<Object[]> findAllResumenAdminRaw();

    Optional<Cliente> findByCurp(String curp);

    Optional<Cliente> findByRfc(String rfc);

    Optional<Cliente> findByCorreo(String correo);

    boolean existsByCurp(String curp);

    boolean existsByRfc(String rfc);

    boolean existsByCorreo(String correo);

    List<Cliente> findByActivoTrueOrderByFechaRegistroAsc();

    List<Cliente> findByFechaRegistroBetweenOrderByFechaRegistroAsc(LocalDateTime inicio, LocalDateTime fin);
}
