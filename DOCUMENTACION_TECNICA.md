# DOCUMENTACIÓN TÉCNICA Y GUÍA DE DEMOSTRACIÓN ACADÉMICA
# Plataforma Integral de Pagos de Servicios y Recargas con Integración Gesto Pago

---

## 1. Resumen Ejecutivo y Ficha Técnica del Proyecto

La **Plataforma Integral de Pagos de Servicios y Recargas** es una solución de grado empresarial y demostración académica diseñada para permitir a clientes bancarios realizar pagos de servicios públicos/privados (electricidad, agua, telefonía fija, televisión por cable, gas) y compras de tiempo aire móvil (Telcel, Movistar, AT&T, etc.) de manera unificada, en tiempo real y con estrictos estándares de seguridad bancaria.

El proyecto integra de forma nativa los servicios de la pasarela transaccional **Gesto Pago (PuntoRed)**, una base de datos relacional en la nube **Neon PostgreSQL**, un motor de notificaciones digitales transaccionales vía **Brevo Email API**, y provee dos clientes con **100% de paridad funcional**: una aplicación Web progresiva desarrollada en **React 18/19 + TypeScript + Tailwind CSS** y una aplicación móvil multiplataforma desarrollada en **Flutter 3.47+ / Dart 3.13+ con Material Design 3**, orquestadas por un backend robusto basado en **Java 21 y Spring Boot 3.3.6**.

### Ficha Técnica
| Atributo | Detalle de Implementación |
|---|---|
| **Dominio** | Pagos de Servicios, Recargas Electrónicas y Banca Digital |
| **Integrador Externo** | Gesto Pago (PuntoRed) API Gateway & CDN |
| **Backend Framework** | Java 21 LTS, Spring Boot 3.3.6, Spring Data JPA, OpenFeign, Gradle 8.5 |
| **Frontend Web** | React 18/19, TypeScript, Vite 8.3, Tailwind CSS v4, Lucide React |
| **Frontend Móvil** | Flutter 3.47.2, Dart 3.13.2, Provider State Management, Material Design 3 |
| **Base de Datos** | PostgreSQL (Neon Cloud Serverless) & H2 In-Memory para pruebas unitarias |
| **Notificaciones** | Brevo Transactional Email REST API |
| **Control de Calidad** | SonarQube Server 9.9.8 LTS (>90% cobertura de pruebas en paquetes clave) |
| **Pruebas Automatizadas**| 199 pruebas JUnit backend, 63 pruebas E2E 4-Tier, 11 pruebas Vitest web, 14 pruebas Flutter |

---

## 2. Arquitectura de la Solución (Multi-Tier Platform)

La plataforma está diseñada bajo un modelo de arquitectura multicapa (Multi-Tier & Layered Architecture) desacoplada, orientada al dominio y con separación rigurosa de responsabilidades.

### 2.1. Diagrama de Arquitectura Global

```
+---------------------------------------------------------------------------------------+
|                                    CLIENT TIER                                        |
|                                                                                       |
|   +------------------------------------+    +-------------------------------------+   |
|   |         Frontend Web (React)       |    |       Mobile App (Flutter)          |   |
|   |   - React 18/19 + Vite 8.3         |    |   - Flutter 3.47 + Dart 3.13        |   |
|   |   - Tailwind CSS & Responsive UI   |    |   - Material Design 3               |   |
|   |   - Axios Client & AuthContext     |    |   - Provider State Management       |   |
|   +-----------------+------------------+    +------------------+------------------+   |
+---------------------|------------------------------------------|----------------------+
                      | HTTP/REST (JSON)                         | HTTP/REST (JSON)
                      | Puerto 10000 / 5173                      | Puerto 10000
+---------------------v------------------------------------------v----------------------+
|                         BACKEND TIER (Spring Boot 3.3.6 / Java 21)                    |
|                                                                                       |
|   +-------------------------------------------------------------------------------+   |
|   |  Capa de Controladores (REST Controllers)                                     |   |
|   |  - AuthController: /api/v1/auth (login, logout, me)                           |   |
|   |  - GestoPagoController: /api/v1/gestopago (productos, agrupados)              |   |
|   |  - PagoServicioController: /api/v1/pagos (validar-referencia, 2fa, servicios,  |   |
|   |                            historial)                                         |   |
|   +---------------------------------------+---------------------------------------+   |
|                                           |                                           |
|   +---------------------------------------v---------------------------------------+   |
|   |  Capa de Servicios de Negocio (Services & Implementations)                    |   |
|   |  - AuthService: Autenticación, bloqueo por fuerza bruta, token JWT            |   |
|   |  - GestoPagoProductService: Cache y consulta dinámica de catálogo            |   |
|   |  - GestoPagoTokenService: Gestión y renovación de token upstream GestoPago    |   |
|   |  - PagoServicioService: Validación de referencias, ciclo 2FA, idempotencia,   |   |
|   |                         cifrado AES-256, deducción atómica de saldo           |   |
|   |  - ComprobanteEmailService: Generación y despacho de comprobantes HTML Brevo  |   |
|   |  - Soporte2FAService: Generación de OTP de 6 dígitos con vigencia de 300s     |   |
|   +-------------------+-----------------------------------+-----------------------+   |
|                       |                                   |                           |
|   +-------------------v-------------------+   +-----------v-----------------------+   |
|   |  Capa de Repositorios (Data Access)   |   |  Capa de Clientes (OpenFeign)     |   |
|   |  - ClienteRepository                  |   |  - GestoPagoAuthClient            |   |
|   |  - LoginClienteRepository             |   |  - GestoPagoProductClient         |   |
|   |  - CuentaRepository                   |   |  - GestoPagoTxClient              |   |
|   |  - SaldoRepository (@Lock PESSIMISTIC)|   |  - BrevoEmailClient               |   |
|   |  - PagoServicioRepository             |   |                                   |   |
|   |  - GestoPagoProductCacheRepository    |   |                                   |   |
|   +-------------------+-------------------+   +-----------+-----------------------+   |
+-----------------------|-----------------------------------|---------------------------+
                        |                                   |
+-----------------------v-------------------+   +-----------v---------------------------+
|               DATABASE TIER               |   |        EXTERNAL SERVICES TIER         |
|   PostgreSQL Serverless (Neon Cloud)      |   |  1. Gesto Pago API Gateway (HTTP XML) |
|   - Esquema Clientes y Cuentas            |   |     - authenticate, getProductList.do |
|   - Bitácora de Transacciones             |   |     - sendTx.do (AES-256), verifyTx.do|
|   - Auditoría de Saldos y Estados         |   |  2. Gesto Pago CDN (Logos PNG)        |
|                                           |   |  3. Brevo Transactional Email API     |
+-------------------------------------------+   +---------------------------------------+
```

---

## 3. Diseño Detallado por Capas del Backend

### 3.1. Capa de Controladores (REST Controllers)
La capa de controladores expone endpoints estandarizados siguiendo los principios RESTful, gestionando serialización JSON estricta y códigos HTTP adecuados:

1. **`AuthController` (`/api/v1/auth`)**:
   - `POST /login`: Valida usuario y contraseña contra la tabla `login_clientes`. Implementa protección contra fuerza bruta (bloqueo tras 5 intentos fallidos consecutivos). Retorna datos del cliente, número de cuenta de 16 dígitos y saldo disponible.
   - `POST /logout`: Invalida la sesión activa del usuario de manera inmediata.
   - `GET /me`: Obtiene el perfil del usuario autenticado sincronizando su saldo y estado de cuenta más reciente.

2. **`GestoPagoController` (`/api/v1/gestopago`)**:
   - `GET /productos`: Retorna la lista plana de productos y servicios provista dinámicamente por Gesto Pago.
   - `GET /productos/agrupados`: Retorna el catálogo estructurado por `tipoFront` (1 = Recargas Telefónicas, 2 = Servicios Básicos y Facturas), facilitando la categorización en clientes web y móviles.

3. **`PagoServicioController` (`/api/v1/pagos`)**:
   - `POST /validar-referencia`: Endpoint crítico de validación previa que comprueba las reglas del servicio (`tipoFront`, longitud, formato numérico, dígito verificador). **Bloquea el flujo transaccional si la referencia es inválida.**
   - `POST /2fa/generar`: Genera un código OTP numérico de 6 dígitos con vigencia estricta de 300 segundos (5 minutos) para la cuenta del cliente.
   - `POST /servicios`: Orquesta la ejecución del pago: valida código 2FA de un solo uso, verifica saldo disponible, ejecuta bloqueo concurrente de cuenta, envía la transacción cifrada a Gesto Pago, descuenta saldo atómicamente, genera el folio `FT-...` y dispara el comprobante por correo electrónico.
   - `POST /historial`: Consulta las transacciones ejecutadas exclusivamente por la cuenta solicitada, garantizando el aislamiento de datos por usuario.

### 3.2. Capa de Servicios y Lógica de Negocio (Service & ServiceImpl)
Contiene las reglas de negocio bancario y las políticas de integridad financiera:
- **`AuthServiceImpl`**: Implementa el hash **BCrypt** para contraseñas de clientes (con migración transparente desde hashes legados), el contador de reintentos fallidos en base de datos (`intentos_fallidos`), el bloqueo de cuenta (`bloqueado = true`), y la generación de tokens de sesión con TTL configurable.
- **`GestoPagoProductServiceImpl`**: Implementa una estrategia de caché en dos niveles (memoria concurrente y base de datos con TTL de 1 hora) para optimizar los tiempos de respuesta del catálogo de Gesto Pago sin sacrificar dinamismo.
- **`GestoPagoTokenServiceImpl`**: Maneja el ciclo de vida del token JWT de Gesto Pago para autenticación contra el distribuidor `${GESTOPAGO_ID_DISTRIBUIDOR}` y dispositivo `${GESTOPAGO_CODIGO_DISPOSITIVO}`, renovándolo automáticamente antes de su expiración.
- **`PagoServicioServiceImpl`**: Núcleo transaccional de la plataforma. Integra:
  - *Ventana de Idempotencia*: Detección de peticiones duplicadas idénticas dentro de una ventana de 2 minutos (HTTP 409 Conflict).
  - *Bloqueo Concurrente*: `ReentrantLock` por cuenta en memoria JVM combinado con `@Lock(LockModeType.PESSIMISTIC_WRITE)` en la base de datos para evitar condiciones de carrera (*race conditions*) en el saldo.
  - *Protección Anti-Manipulación de Precios*: Comprobación de que el monto a cobrar coincida exactamente con el precio estipulado en el catálogo para productos de precio fijo.
  - *Cifrado de Carga Útil*: Cifrado AES-256-CBC mediante `AesGestoPagoUtil` antes de invocar `sendTx.do`.
  - *Mecanismo de Reconciliación*: Consulta automática mediante `verifyTx.do` ante timeouts de red o códigos de estado ambiguos (como código 82).
- **`ComprobanteEmailServiceImpl`**: Construye plantillas HTML enriquecidas y despacha el correo transaccional utilizando el cliente `BrevoEmailClient`.

### 3.3. Capa de Repositorios (Data Access)
Construida con Spring Data JPA sobre entidades mapeadas para PostgreSQL:
- `SaldoRepository`: Incluye la consulta con bloqueo pesimista:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Saldo> findByCuentaId(Long cuentaId);
  ```
- `PagoServicioRepository`: Gestiona la persistencia de transacciones exitosas, fallidas y pendientes, con índices por número de cuenta y fecha descendente.
- `LoginClienteRepository`: Gestiona el estado de acceso de cada cliente y el conteo de intentos fallidos.

### 3.4. Capa de Clientes Externos (OpenFeign)
- `GestoPagoProductClient`: Consume `GET /gestopago/ws/rest/catalogo/getProductList.do`.
- `GestoPagoTxClient`: Consume `POST /gestopago/ws/rest/transaccion/sendTx.do` y `verifyTx.do`.
- `GestoPagoAuthClient`: Consume `POST /gestopago/ws/rest/autenticacion/authenticate`.
- `BrevoEmailClient`: Consume `POST https://api.brevo.com/v3/smtp/email` inyectando el header `api-key`.

### 3.5. Capa de Configuración y Seguridad
- **`CorsConfig`**: Habilita CORS de manera estricta para orígenes de desarrollo y producción (incluyendo `http://localhost:5173`, `http://localhost:3000`, `http://127.0.0.1:5173`, y orígenes de Flutter Web). Permite métodos `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS` y headers transaccionales.
- **`JacksonConfig`**: Deshabilita coerción automática de escalares a strings y garantiza serialización estricta de fechas en formato ISO (`yyyy-MM-dd` / `yyyy-MM-dd'T'HH:mm:ss`).
- **`OpenApi`**: Configura Swagger UI interactivo accesible en `/swagger-ui.html`.

---

## 4. Servicios Externos Consumidos e Integración

### 4.1. Gesto Pago (PuntoRed)
La plataforma interactúa con el ecosistema de Gesto Pago mediante los siguientes componentes:

| Servicio / Endpoint | Método | Formato | Propósito |
|---|---|---|---|
| `.../autenticacion/authenticate` | POST | JSON | Obtiene token de sesión del distribuidor upstream (`${GESTOPAGO_ID_DISTRIBUIDOR}`, `${GESTOPAGO_CODIGO_DISPOSITIVO}`). |
| `.../catalogo/getProductList.do` | GET | XML/JSON | Descarga el catálogo maestro de servicios y productos activos en tiempo real. |
| `.../transaccion/sendTx.do` | POST | Form-Urlencoded (AES-256) | Envía la transacción cifrada al nodo de compensación de Gesto Pago. |
| `.../transaccion/verifyTx.do` | POST | Form-Urlencoded (AES-256) | Reconcilia transacciones pendientes o afectadas por cortes de conectividad. |
| CDN de Logotipos | GET | Imagen PNG | `https://gestopago.portalventas.net/sistema/images/gestopago/servicios/{idServicio}.png` |

#### Especificación de Cifrado AES-256-CBC
Para la ejecución de transacciones en `sendTx.do`, los parámetros se estructuran en formato XML y se cifran con **AES-256-CBC** utilizando la llave y el vector de inicialización (IV) configurados por variables de entorno (`.env`):
- Algoritmo: `AES/CBC/PKCS5Padding`
- API Key / Clave: `${GESTOPAGO_API_KEY}`
- Secret Key / IV: `${GESTOPAGO_SECRET_KEY}` / `${GESTOPAGO_IV}`
- Unidad del distribuidor: `${GESTOPAGO_UNIDAD}`

### 4.2. Brevo Email API (Notificaciones Transaccionales)
- **URL Base**: `https://api.brevo.com/v3/smtp/email`
- **Autenticación**: Header `api-key: ${BREVO_API_KEY}`
- **Remitente**: `Banco Digital - Pagos y Recargas <${BREVO_SENDER_EMAIL}>`
- **Contenido del Comprobante**:
  - Folio único de operación (`FT-yyyyMMddHHmmss-XXXX`)
  - Proveedor y logotipo del servicio pagado
  - Referencia de pago o número telefónico recargado
  - Importe abonado y comisión cobrada ($0.00 MXN)
  - Saldo anterior y saldo remanente actualizado
  - Fecha, hora exacta y leyenda de aviso legal bancario

### 4.3. Base de Datos PostgreSQL (Neon Cloud)
- **Modo**: Servidor administrado en AWS US-East-2 con conexión SSL obligatoria (`sslmode=require`).
- **Esquema**: Multi-tabla con claves foráneas, restricciones de unicidad de cuentas, y bitácora inmutable de folios de pago.

---

## 5. Mecanismos de Seguridad, Concurrencia e Integridad Financiera

### 5.1. Autenticación y Cifrado
Las credenciales de los clientes se almacenan en la tabla `login_clientes` aplicando **BCrypt** (`BCryptPasswordEncoder`). Nunca se almacenan contraseñas en texto claro.

### 5.2. Protección Contra Fuerza Bruta
Si un usuario introduce credenciales erróneas durante **5 intentos consecutivos**, el sistema bloquea inmediatamente la cuenta asignando `bloqueado = true` en base de datos. Cualquier intento posterior es rechazado con el código de error correspondiente (`AUTH_02 - Cuenta bloqueada`).

### 5.3. Generación y Consumo Atómico de 2FA OTP
- El código OTP se genera mediante un generador criptográficamente seguro (SecureRandom) con longitud de **6 dígitos numéricos**.
- Posee un tiempo de vida (TTL) de **300 segundos (5 minutos)**.
- **Un solo uso atómico**: Al procesarse una transacción exitosa o fallida, el token queda consumido inmediatamente. Reutilizar el mismo código 2FA resulta en rechazo inmediato (HTTP 401).

### 5.4. Bloqueo Concurrente y Transaccional
Para evitar la condición de *Double-Spending* (gasto doble simultáneo):
1. Se adquiere un `ReentrantLock` por número de cuenta en memoria JVM.
2. Dentro de la transacción `@Transactional`, se ejecuta un `SELECT ... FOR UPDATE` mediante `@Lock(LockModeType.PESSIMISTIC_WRITE)` sobre la fila de saldo de la cuenta.
3. Se verifica que `saldoActual >= monto`. Solo si la API de Gesto Pago responde con éxito (`CODIGO = 01`), se aplica la resta del saldo y se confirma la transacción.

### 5.5. Ventana de Idempotencia Local
Para proteger al cliente de reintentos involuntarios por doble clic o fallos momentáneos de red:
- Se registra un hash de la transacción: `hash(numeroCuenta + idProducto + referencia + monto)`.
- Si ingresa una petición idéntica dentro de una ventana de **120 segundos (2 minutos)**, el sistema la rechaza preventivamente con código **HTTP 409 Conflict**.

### 5.6. Escudo Anti-Manipulación de Precios
Si un usuario altera la petición HTTP enviando un monto diferente al costo oficial de un paquete en el catálogo de Gesto Pago (por ejemplo, intentar comprar un paquete Telcel de $100.00 pagando $1.00), el backend detecta la discrepancia contra la lista autorizada y aborta la transacción con **HTTP 400 Bad Request**.

---

## 6. Matriz de Paridad Funcional (100% Web vs Flutter)

Ambas aplicaciones cliente fueron construidas para cumplir exactamente los mismos requisitos de interfaz, interacción y validación:

| Pilar Funcional | Frontend Web (React + Tailwind) | Mobile App (Flutter + MD3) | Paridad |
|---|---|---|:---:|
| **Pilar 1: Autenticación y Sesión** | Formulario con selector rápido de cuentas demo, visualización de saldo inicial, enmascaramiento de cuenta `5225 •••• •••• 3705`, temporizador de inactividad con cuenta regresiva visible, logout sincronizado. | Pantalla de Login con tarjetas de cuentas demo interactivas, avatar de usuario, cuenta enmascarada, contador de sesión en AppBar, logout reactivo. | **100%** |
| **Pilar 2: Catálogo Dinámico** | Carga directa de API `/productos/agrupados`, tabs/filtros por categoría (Todos, Recargas, Servicios), buscador en tiempo real por texto, badges de precio y comisión, logos dinámicos vía CDN de Gesto Pago con fallback visual. | Grid y lista responsiva de servicios con chips de filtro de categoría (`CategoryChips`), buscador interactivo, logos CDN con `BrandAvatar` y fallback de iniciales, indicador visual de tipo de cobro (Fijo vs Abierto). | **100%** |
| **Pilar 3: Validación de Referencias** | Modal / Drawer de pago con validación en tiempo real contra `/validar-referencia`. Bloqueo visual estricto: botón "Continuar al 2FA" deshabilitado hasta recibir `esValida = true`. Validación de 10 dígitos para telefonía y hasta 30 caracteres para recibos. | `PaymentFlowDialog` con campo de referencia reactivo, llamada asíncrona a `/validar-referencia`, banner informativo de reglas del servicio, bloqueo incondicional del avance si la referencia no pasa la regla. | **100%** |
| **Pilar 4: Pago y 2FA OTP** | Resumen financiero antes de pagar (Servicio, Referencia, Monto, Comisión, Saldo Resultante). Botón para generar OTP con temporizador de 300s, input de 6 dígitos, ejecución segura y modal de comprobante con folio `FT-...` y confirmación de correo. | Diálogo paso a paso (Stepper): Resumen -> Generación de OTP -> Input numérico de 6 dígitos -> Procesamiento con animación de carga -> `ReceiptDialog` con botón para ver ticket y enviar comprobante. | **100%** |
| **Pilar 5: Historial de Transacciones** | Vista `/historial` con lista cronológica de transacciones del usuario, tarjeta bancaria con saldo actual, filtro por estado, modal de detalle de ticket por cada operación realizada. | Pantalla `HistoryScreen` con tarjetas de transacción (`BankingCard` + listado), filtrado exclusivo de la cuenta activa, bottom sheet interactivo (`HistoryReceiptSheet`) con desglose de ticket. | **100%** |

---

## 7. Métricas de Calidad de Código y Cobertura SonarQube

El proyecto fue analizado e indexado en el servidor SonarQube 9.9.8 LTS local (puerto 9000) bajo el proyecto `prueba_aplicaciones_web_progresivas`, satisfaciendo con holgura el requisito R7 (>80% de cobertura en los paquetes `service`, `serviceimpl` y `config`).

### Métricas Oficiales Obtenidas en SonarQube
```json
{
  "coverage": "90.5%",
  "line_coverage": "95.7%",
  "branch_coverage": "79.8%",
  "ncloc": 3774,
  "bugs": 5,
  "vulnerabilities": 3,
  "code_smells": 52
}
```

- **Cobertura General de Pruebas**: **90.5%** (Supera el umbral mínimo exigido del 80%).
- **Cobertura de Líneas**: **95.7%** en las clases de lógica transaccional y configuración.
- **Líneas de Código Analizadas (NCLOC)**: 3,774 líneas de código Java puro.

---

## 8. Guía de Demostración Académica Paso a Paso (Evaluación para el Profesor)

Esta guía describe el recorrido exacto para validar y evaluar la plataforma durante la sesión académica, tanto en la interfaz Web como en la aplicación móvil Flutter.

### 8.1. Prerrequisitos y Arranque de Servidores

1. **Backend Spring Boot** (Puerto 10000 o mediante wrapper):
   ```powershell
   .\gradlew bootRun
   ```
2. **Frontend Web** (Puerto 5173):
   ```powershell
   cd frontend-web
   npm run dev
   # Abrir en navegador: http://localhost:5173
   ```
3. **Mobile Flutter Web** (Para demostración en navegador o emulador):
   ```powershell
   cd mobile-flutter
   flutter run -d chrome
   ```

### 8.2. Cuentas de Demostración Disponibles

*(Las credenciales de prueba se administran exclusivamente en el entorno seguro local / gestor de secretos; nunca se documentan contraseñas en texto claro en el repositorio.)*

| Usuario | Cuenta Asignada (Enmascarada) | Perfil / Rol |
|---|---|---|
| `<DEMO_USER_1>` | `************3705` | Cuenta Principal |
| `<DEMO_USER_2>` | `************4504` | Cuenta Secundaria |
| `<DEMO_USER_3>` | `************3456` | Cuenta de Pruebas Automatizadas |
| `<DEMO_USER_4>` | `************4444` | Cuenta de Alta Capacidad |

---

### 8.3. Recorrido de Demostración Paso a Paso

#### Paso 1: Inicio de Sesión y Autenticación Unificada (R1)
1. Ingrese a la aplicación (Web o Flutter).
2. Se desplegará la pantalla de bienvenida para inicio de sesión (con opción de contraseña o huella digital en móvil).
3. Ingrese sus credenciales autorizadas.
4. Pulse **"Iniciar Sesión"**.
5. **Observación esperada**:
   - Acceso concedido instantáneo mediante token Bearer autenticado.
   - En el encabezado y en la tarjeta bancaria se muestra el nombre del cliente, el número de cuenta enmascarado `************3705` y el saldo real.
   - Se activa un temporizador de sesión con cuenta regresiva (300 segundos).

#### Paso 2: Exploración de Catálogo Dinámico en Tiempo Real (R2)
1. En la pantalla principal, observe el catálogo de servicios obtenido en vivo desde Gesto Pago.
2. Utilice los chips de categoría:
   - Haga clic en **"Recargas"**: Observe los operadores móviles (Telcel, Movistar, AT&T, Bait).
   - Haga clic en **"Servicios"**: Observe los proveedores de servicios (CFE, Telmex, Naturgy, Sky).
3. Utilice la barra de búsqueda escribiendo *"Telcel"* o *"CFE"*.
4. **Observación esperada**:
   - Cada tarjeta muestra el logotipo oficial servido desde el CDN de Gesto Pago.
   - Se muestra si el monto es fijo o configurable por el usuario.

#### Paso 3: Validación y Bloqueo de Referencia (R3)
1. Seleccione el servicio **"Telcel - Paquete Amigo 20"** ($20.00 MXN).
2. Se abrirá la ventana modal de pago.
3. Intente ingresar un teléfono incompleto: `5512345` (7 dígitos) y presione validar.
4. **Comportamiento esperado (Bloqueo)**:
   - El sistema muestra una alerta roja: *"El número de teléfono debe contener exactamente 10 dígitos numéricos"*.
   - **El botón "Continuar al 2FA" permanece deshabilitado.** Es imposible continuar al pago.
5. Ingrese ahora un número válido de 10 dígitos: `5512345678`.
6. Presione **"Validar Referencia"**.
7. **Comportamiento esperado (Desbloqueo)**:
   - Indicador verde de validación exitosa.
   - El botón para avanzar al paso 2FA se habilita.

#### Paso 4: Resumen Transaccional y Confirmación 2FA (R4)
1. Haga clic en **"Continuar al 2FA"**.
2. Revise el resumen financiero previo al cobro:
   - Servicio: Telcel Paquete Amigo 20
   - Referencia: `5512345678`
   - Importe: $20.00 MXN
   - Saldo disponible actual: $60.00 MXN
   - Saldo posterior estimado: $40.00 MXN
3. Presione el botón **"Generar Código 2FA"**.
4. El sistema enviará el código OTP de 6 dígitos al correo registrado del cliente vía **Brevo API** con una vigencia activa de 5 minutos.
5. Ingrese el código OTP recibido en los campos correspondientes.

#### Paso 5: Procesamiento de Pago y Emisión de Folio de Ticket (R4)
1. Pulse **"Confirmar y Pagar"**.
2. El sistema muestra un indicador de carga mientras procesa la transacción cifrada en AES-256 hacia Gesto Pago.
3. **Resultado de la Operación**:
   - Se presenta el modal de resultado con el **Folio Oficial de Ticket**: ej. `FT-20261114023015-8492`.
   - Se indica el número de autorización bancaria y la hora de confirmación.
   - El saldo de la cuenta se actualiza en pantalla inmediatamente a **$40.00 MXN**.
   - Se dispara la notificación automática del comprobante de pago al correo registrado (`usuario@ejemplo.com`) vía Brevo API.

#### Paso 6: Consulta y Auditoría de Historial de Transacciones (R5)
1. Navegue a la pestaña **"Historial"** en la barra de navegación superior (Web) o inferior (Flutter).
2. **Comportamiento esperado**:
   - Se listan únicamente las transacciones pertenecientes a la cuenta autenticada (`************3705`).
   - En la parte superior aparece la transacción recién realizada con su logotipo, proveedor (Telcel), referencia, fecha, importe (-$20.00 MXN) y estado **EXITOSA**.
3. Haga clic sobre la transacción para abrir el comprobante detallado con el folio de ticket `FT-...`.

#### Paso 7: Comprobación de Seguridad y Casos Límite (R1, R4)
1. **Reintento inmediato (Idempotencia)**: Intente realizar exactamente el mismo pago con la misma referencia dentro de los primeros 2 minutos; el sistema reportará que la transacción ya se encuentra en proceso o registrada.
2. **Saldo Insuficiente**: Intente realizar un pago de servicio por $50.00 MXN (el saldo disponible actual es $40.00 MXN); el sistema denegará la transacción por fondos insuficientes antes de afectar el proveedor externo.
3. **Cierre de Sesión**: Presione **"Cerrar Sesión"**. La sesión se destruye tanto en el cliente como en el backend, redirigiendo de inmediato a la pantalla de Login.

---

## 9. Inventario de Pruebas Automatizadas

La calidad de la plataforma está respaldada por cuatro suites de pruebas automatizadas complementarias:

| Suite | Comando de Ejecución | Pruebas | Resultado | Tasa de Éxito |
|---|---|:---:|:---:|:---:|
| **Backend Unit & Integration** | `.\gradlew test` | 199 | 199 Pasadas, 0 Fallos | **100%** |
| **E2E Opaque-Box (Tiers 1-4)** | `python -u e2e-tests\run_e2e_tests.py --mock --tier all` | 63 | 63 Pasadas, 0 Fallos | **100%** |
| **Frontend Web Build & Test** | `cd frontend-web && npm run build && npm test` | 11 | 11 Pasadas, 0 Fallos | **100%** |
| **Mobile Flutter Analyze & Test** | `cd mobile-flutter && flutter analyze && flutter test` | 14 | 14 Pasadas, 0 Errores | **100%** |
| **Mobile Flutter Web Build** | `cd mobile-flutter && flutter build web` | Build | Compilación Exitosa | **100%** |
| **Cobertura SonarQube** | `http://localhost:9000` (Proyecto: `prueba_aplicaciones_web_progresivas`) | N/A | **90.5% Cobertura** | **Superada (>80%)** |

---

## 10. Conclusiones Técnicas

La solución desarrollada satisface con rigor profesional y académico la totalidad de los requerimientos funcionales (R1-R6), no funcionales (R7) y documentales (R8):
1. **Integración Real con Gesto Pago**: El catálogo, las validaciones y los cobros interactúan con la infraestructura del agregador, garantizando robustez mediante cifrado AES-256 y mecanismos de reconciliación.
2. **Paridad Total Multiplataforma**: La experiencia en React Web y Flutter Mobile es visualmente consistente, intuitiva y funcionalmente idéntica.
3. **Integridad Bancaria y Seguridad**: El uso de bloqueos pesimistas, protección contra fuerza bruta, tokens 2FA de un solo uso y filtros de idempotencia blindan la plataforma contra fraudes y desbalances contables.
4. **Verificabilidad Total**: Cada componente cuenta con comandos reproducibles de compilación, análisis estático y pruebas automatizadas con 100% de aprobación.
