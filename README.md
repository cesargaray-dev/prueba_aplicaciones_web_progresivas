# 🏦 Sistema Bancario de Pago de Servicios & Recargas

API RESTful bancaria empresarial desarrollada con **Spring Boot 3**, **Java 21**, **PostgreSQL (Neon Cloud)** y desplegada en **Render**. Integra el módulo transaccional de pagos de servicios y tiempo aire con **GestoPago (PuntoRed)**, autenticación de dos factores (**2FA OTP**), cifrado **AES-256-CBC**, validación de saldos y emisión automática de comprobantes digitales vía **Brevo (Sendinblue)**.

---

## 🌐 Enlaces del Proyecto

* **API en Producción (Render Swagger UI):** [https://prueba-aplicaciones-web-progresivas.onrender.com/swagger-ui/index.html](https://prueba-aplicaciones-web-progresivas.onrender.com/swagger-ui/index.html)
* **Repositorio GitHub:** [https://github.com/cesargaray-dev/prueba_aplicaciones_web_progresivas](https://github.com/cesargaray-dev/prueba_aplicaciones_web_progresivas)
* **Rama de Producción:** `dev`
* **Rama de Desarrollo:** `feature/pago-servicios`

---

## 🚀 Características y Reglas de Negocio Implementadas

### 1. Pagos de Servicios y Recargas con GestoPago (`sendTx.do`)
* **Diferenciación de flujo (`tipoFront`):**
  * `tipoFront = 1` (Recargas de Tiempo Aire): Requiere número telefónico de 10 dígitos.
  * `tipoFront = 2` (Servicios con Recibo - CFE, Izzi, Telmex, etc.): Requiere número de referencia del recibo.
* **Cifrado AES-256-CBC:**
  * Generación de cadena alfanumérica aleatoria única de 10 caracteres (`random10`).
  * Encriptación simétrica AES-256 con padding PKCS5, vector de inicialización (`IV`) y clave secreta compartida con PuntoRed.
  * Codificación en Base64 para el parámetro `signed`.

### 2. Autenticación de Dos Factores (2FA OTP)
* **Generación de Códigos Temporales:** Genera un código numérico aleatorio de 6 dígitos mediante `POST /api/v1/pagos/2fa/generar`.
* **Vigencia:** 5 minutos (300 segundos). Soporta almacenamiento en memoria y en caché distribuida con **Redis**.
* **Protección contra Doble Uso (*Single-Use*):** El código 2FA se destruye e invalida inmediatamente tras el primer pago exitoso (`consumirCodigo2FA`), impidiendo cobros duplicados por reintento.

### 3. Regla Crítica de Deducción Segura del Dinero
* **Integridad Transaccional (`@Transactional`):** El saldo de la cuenta bancaria **ÚNICAMENTE se descuenta** si el proveedor GestoPago confirma la transacción con el código de éxito:
  $$\text{CODIGO} = \mathbf{01}$$
* Si el proveedor responde con cualquier otro código (ej. `02` Saldo insuficiente en bolsa, `06` Transacción duplicada, `82` Timeout):
  * **No se resta dinero de la cuenta.**
  * Se cancela la operación y se informa el motivo exacto devuelto por GestoPago.

### 4. Control Estricto de Saldo Insuficiente
* Antes de conectar con el proveedor externo, el sistema valida que el saldo disponible sea mayor o igual al monto a pagar:
  $$\text{Saldo Disponible} \ge \text{Monto a Pagar}$$
* Si la cuenta no tiene fondos suficientes, se rechaza de inmediato con `HTTP 400 Bad Request` indicando el saldo disponible y el monto solicitado.

### 5. Cuentas Bancarias de 16 Dígitos
* Estandarización a **16 dígitos numéricos** en la generación de cuentas (`ClienteServiceImpl`) y en todas las validaciones de entrada (`@Pattern(regexp = "^\\d{16}$")`).

### 6. Historial Cronológico de Pagos (`POST /api/v1/pagos/historial`)
* Permite auditar y consultar todas las transacciones realizadas por una cuenta bancaria, ordenadas cronológicamente de la más reciente a la más antigua con folio de ticket, autorización, servicio, montos y saldos restantes.

### 7. Emisión de Comprobantes Digitales con Brevo
* Notificación automática por correo electrónico al cliente titular en cuanto un pago resulta exitoso.
* Plantilla HTML bancaria responsive que cumple con los requerimientos solicitados:
  1. **Logotipo corporativo** institucional.
  2. **Número de Autorización** del proveedor.
  3. **Servicio y Producto** (ej. IZZI Telecom).
  4. **Teléfono** (si fue recarga).
  5. **Referencia de Pago** (si fue servicio con recibo).
  6. **Monto debitado y Comisión**.
  7. **Fecha y Hora** de la operación.
  8. **Leyenda oficial para aclaraciones** con números de contacto y plazos legales.

---

## 📸 Evidencias de Funcionamiento

### 1. Comprobante Digital Recibido por Correo (Brevo)

| Encabezado y Detalles del Pago | Leyenda Oficial de Aclaraciones |
| :---: | :---: |
| <img src="docs/images/comprobante_email_parte1.png" alt="Comprobante Digital Recibido por Correo" width="340"/> | <img src="docs/images/comprobante_email_parte2.png" alt="Leyenda de Aclaraciones en el Correo" width="340"/> |

> **Leyenda incluida en el correo:**
> *"Conserve este comprobante digital como constancia oficial de pago. Para cualquier duda, aclaración, reporte de saldo o reclamación relacionada con esta transacción o la aplicación del servicio por parte del proveedor, comuníquese a nuestra Línea de Aclaraciones al 800-226-2600 (sin costo, las 24 horas) o envíe un correo a aclaraciones@bancodigital.com dentro de los 30 días naturales posteriores a la fecha de operación. Favor de mencionar el Número de Autorización y su Folio de Ticket."*

---

### 2. Validación de Saldo Insuficiente (Swagger UI)

Cuando una cuenta con saldo menor al monto intenta realizar un pago, la solicitud es rechazada de inmediato y el dinero no se debita:

![Validación de Saldo Insuficiente](docs/images/swagger_saldo_insuficiente.png)

```json
{
  "codigo": 1,
  "mensaje": "Saldo insuficiente en la cuenta. Saldo disponible: $80.00, Monto a pagar: $500.00"
}
```

---

### 3. Consulta de Historial de Transacciones (Swagger UI)

El endpoint `POST /api/v1/pagos/historial` devuelve la lista cronológica completa de movimientos:

![Historial de Pagos de Servicios](docs/images/swagger_historial_pagos.png)

---

## 🧪 Cuentas de Prueba Disponibles para Evaluación

Las siguientes cuentas están activas y listas en la base de datos de producción:

| Número de Cuenta (16 dígitos) | Titular | Saldo Disponible | Correo de Notificación |
| :---: | :---: | :---: | :---: |
| `5225253189113705` | Cesar Enrique Garay Garcia | **$60.00 MXN** | `cesarenriquegaraygarcia@gmail.com` |
| `8906887292154504` | Carlos Sánchez | **$60.00 MXN** | `cesarenriquegaraygarcia50@gmail.com` |

---

## 🛠️ Guía Rápida de Prueba en Swagger

Para probar la API en [Render Swagger UI](https://prueba-aplicaciones-web-progresivas.onrender.com/swagger-ui/index.html):

### Paso 1: Generar código 2FA
* **Endpoint:** `POST /api/v1/pagos/2fa/generar`
* **Body:**
  ```json
  {
    "numeroCuenta": "5225253189113705"
  }
  ```
* Copia el `codigo2FA` devuelto (vigencia de 5 minutos).

### Paso 2: Realizar Pago de Servicio
* **Endpoint:** `POST /api/v1/pagos/servicios`
* **Body:**
  ```json
  {
    "numeroCuenta": "5225253189113705",
    "idServicio": 76,
    "idProducto": 205,
    "monto": 20.00,
    "referencia": "25720401",
    "codigo2FA": "TU_CODIGO_2FA_GENERADO"
  }
  ```
* **Respuesta esperada:** HTTP 200 OK con folio de ticket, número de autorización y saldo restante actualizado.
* El correo llegará inmediatamente a la bandeja asociada.

### Paso 3: Consultar Historial
* **Endpoint:** `POST /api/v1/pagos/historial`
* **Body:**
  ```json
  {
    "numeroCuenta": "5225253189113705"
  }
  ```

---

## 📦 Stack Tecnológico

* **Lenguaje:** Java 21 (LTS)
* **Framework:** Spring Boot 3.3.6 (Web, Data JPA, OpenFeign, Validation)
* **Base de Datos:** PostgreSQL 16/18 en Neon Cloud
* **Migraciones:** Flyway Database Migrations
* **Caché:** Redis / Spring Data Redis
* **Integración Externa:** PuntoRed / GestoPago (`sendTx.do`) con Cifrado AES-256
* **Email Service:** Brevo REST API v3 (Sendinblue)
* **Documentación:** SpringDoc OpenAPI 3.0 / Swagger UI
* **Despliegue:** Render Cloud Platform
