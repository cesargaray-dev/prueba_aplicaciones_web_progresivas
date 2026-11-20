# Project: Plataforma Integral de Pagos de Servicios con Gesto Pago

## Architecture
- **Backend**: Java 21, Spring Boot 3.3.6, Gradle 8.5. Layered architecture:
  - `controller`: REST endpoints for Auth, Gesto Pago Catalog, Reference Validation, 2FA Payment Processing, and Transaction History.
  - `service` & `service.Impl`: Business logic, concurrency locks (`ReentrantLock`), DB locks (`PESSIMISTIC_WRITE`), AES-256-CBC encryption, Brevo email receipts, idempotency, and session management.
  - `client`: OpenFeign clients for Gesto Pago upstream API and Brevo SMTP API.
  - `repositorys`: Spring Data JPA repositories with PostgreSQL (Neon Cloud) and H2 for unit testing.
  - `config`: Security, CORS, Redis, Mail, Swagger/OpenAPI, and Web configuration.
- **Frontend Web (`/frontend-web`)**:
  - React 18/19, Vite, TypeScript, Tailwind CSS, Lucide React icons, React Router.
  - 5-pillar parity: Auth & session, Dynamic Catalog with provider logos & category chips, Reference input with blocking validation, 2FA modal & payment processing, Transaction history audit trail.
- **Mobile Flutter (`/mobile-flutter`)**:
  - Flutter 3.47.2, Dart 3.13.2, Material Design 3.
  - Complete parity with Web across all 5 pillars using reactive state management and responsive layouts.
- **SonarQube Quality Assurance**:
  - SonarQube running locally on port 9000 via `./gradlew sonar`.
  - Strictly >80% test coverage on packages `service`, `serviceimpl`, and `config`.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Client Auth & Session | Login (`login_clientes`, SHA-256), logout, session timer, user profile | M1, M3, M4 | Survey R1 |
| 2 | Upstream Token Renewal | Gesto Pago JWT provider authentication and automatic renewal | M1 | Survey |
| 3 | Dynamic Catalog | Live catalog from Gesto Pago (`/productos`, `/productos/agrupados`) | M1, M3, M4 | Survey R2 |
| 4 | Provider Logo CDN | Dynamic logos via Gesto Pago CDN (`/servicios/{idServicio}.png`) | M1, M3, M4 | Survey R2 |
| 5 | Reference Rules & Check Digit | Validation for `tipoFront` 1 & 2, `tipoReferencia` a/b/c/bc, and Modulo 10/11 | M1 | Survey R3 |
| 6 | Reference Validation Endpoint | `POST /api/v1/pagos/validar-referencia` blocking payment if invalid | M1, M3, M4 | Survey R3 |
| 7 | 2FA OTP Generation | 6-digit OTP generation with 300-second TTL and countdown timer | M1, M3, M4 | Survey R4 |
| 8 | 2FA OTP Consumption | Atomic single-use validation and invalidation upon payment confirmation | M1, M3, M4 | Survey R4 |
| 9 | Concurrency & DB Locking | `ReentrantLock` per account and `@Lock(PESSIMISTIC_WRITE)` on balances | M1 | Survey R4 |
| 10 | Anti-Tampering Price Check | Amount verification against catalog fixed prices and authorized ranges | M1 | Survey R4 |
| 11 | Local Idempotency Window | 2-minute duplicate payment prevention | M1 | Survey R4 |
| 12 | AES-256-CBC Encryption | Payload encryption for upstream `sendTx.do` transaction execution | M1 | Survey R4 |
| 13 | Timeout Reconciliation | Automatic query via `verifyTx.do` on network timeouts / code 82 | M1 | Survey R4 |
| 14 | Atomic Balance Deduction | Deduction strictly conditional on provider confirming `CODIGO = 01` | M1 | Survey R4 |
| 15 | Ticket Folio Generation | Unique receipt ticket folio: `FT-yyyyMMddHHmmss-XXXX` | M1, M3, M4 | Survey R4 |
| 16 | Digital Email Receipt | HTML payment voucher sent via Brevo with official legal disclaimer | M1, M3, M4 | Survey R4 |
| 17 | Transaction History | Chronological history filtered exclusively by authenticated user account | M1, M3, M4 | Survey R5 |
| 18 | Global CORS Configuration | Spring Boot CORS filter allowing Web (5173) and Flutter origins | M1 | Survey R6 |
| 19 | SonarQube >80% Coverage | Unit tests on `service`, `serviceimpl`, `config` exceeding 80% coverage | M2 | Survey R7 |
| 20 | Frontend Web App | React + Vite + Tailwind CSS responsive application in `/frontend-web` | M3 | Survey R6 |
| 21 | Mobile Flutter App | Flutter + Dart Material Design application in `/mobile-flutter` | M4 | Survey R6 |
| 22 | Technical Documentation | Architecture document, layered design, APIs, and demo guide | M5 | Survey R8 |
| 23 | E2E Testing Suite | Opaque-box automated test suite (Tiers 1-4) and `TEST_READY.md` | E2E-Track | Dual-Track |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| E2E | E2E Testing Track | Requirement-driven opaque-box test suite (Tiers 1-4) & `TEST_READY.md` | none | COMPLETED |
| M1 | Backend Core & Endpoints | Auth (`login_clientes`), `/validar-referencia`, CORS, DTO enhancements | none | DONE |
| M2 | SonarQube & Coverage Hardening | Test expansion in `service`, `serviceimpl`, `config` >80%, `./gradlew sonar` | M1 | DONE |
| M3 | Frontend Web Application | React + Vite + Tailwind CSS in `/frontend-web` with full parity | M1 | DONE |
| M4 | Mobile Flutter Application | Flutter + Dart Material Design in `/mobile-flutter` with full parity | M1 | DONE |
| M5 | Final Integration & Docs | 100% E2E test pass, SonarQube sign-off, Technical Architecture & Demo Guide | M1, M2, M3, M4, E2E | DONE |

## Interface Contracts

### 1. Authentication Contract
- `POST /api/v1/auth/login`
  - Request: `{ "usuario": "string", "contrasena": "string" }`
  - Response (200): `{ "token": "string", "usuario": "string", "clienteId": 1, "nombreCompleto": "string", "email": "string", "numeroCuenta": "16_digits", "saldo": 60.00 }`
  - Error (401): `{ "codigo": "AUTH_01", "mensaje": "Credenciales inválidas" }`
- `POST /api/v1/auth/logout`
  - Request: `{ "usuario": "string" }`
  - Response (200): `{ "mensaje": "Sesión cerrada exitosamente" }`
- `GET /api/v1/auth/me`
  - Query/Header: `?usuario=string` or `Authorization: Bearer <token>`
  - Response (200): User profile, active account number, current balance.

### 2. Reference Validation Contract
- `POST /api/v1/pagos/validar-referencia`
  - Request: `{ "idServicio": 166, "idProducto": 1001, "referencia": "5512345678", "monto": 20.00 }`
  - Response (200): `{ "esValida": true, "mensaje": "Referencia válida", "tipoFront": 1, "tipoReferencia": "a", "requiereMonto": false, "montoSugerido": 20.00 }`
  - Response (400/200 invalid): `{ "esValida": false, "mensaje": "El número de teléfono debe contener exactamente 10 dígitos numéricos" }`

### 3. 2FA Generation & Payment Contract
- `POST /api/v1/pagos/2fa/generar`
  - Request: `{ "numeroCuenta": "16_digits" }`
  - Response (200): `{ "codigo2FA": "123456", "vigenciaSegundos": 300, "fechaExpiracion": "2026-11-14T..." }`
- `POST /api/v1/pagos/servicios`
  - Request: `{ "numeroCuenta": "16_digits", "idServicio": 166, "idProducto": 1001, "monto": 20.00, "telefono": "5512345678", "referencia": "", "codigo2FA": "123456" }`
  - Response (200): `{ "idTransaccion": 1, "numeroAutorizacion": "AUTH...", "folioTicket": "FT-20261114-XXXX", "montoPagado": 20.00, "comision": 0.00, "saldoRestanteCuenta": 40.00, "fechaHoraOperacion": "...", "concepto": "Recarga..." }`

### 4. Transaction History Contract
- `POST /api/v1/pagos/historial`
  - Request: `{ "numeroCuenta": "16_digits" }`
  - Response (200): `{ "numeroCuenta": "16_digits", "totalPagos": 5, "pagos": [ { "idPago": 1, "folioTicket": "FT-...", "numeroAutorizacion": "...", "idServicio": 166, "servicio": "Telcel", "idProducto": 1001, "producto": "Paquete Amigo 20", "referencia": "5512345678", "monto": 20.00, "comision": 0.00, "saldoAnterior": 60.00, "saldoRestante": 40.00, "estado": "EXITOSA", "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/166.png", "fechaOperacion": "..." } ] }`

## Code Layout
- Existing Backend: `src/main/java/com/proyecto/servicios/`
  - `controller/`: `AuthController.java`, `PagoServicioController.java`, `GestoPagoController.java`
  - `service/`: `AuthService.java`, `PagoServicioService.java`, `GestoPagoProductService.java`
  - `service/Impl/`: `AuthServiceImpl.java`, `PagoServicioServiceImpl.java`, `GestoPagoProductServiceImpl.java`
  - `config/`: `CorsConfig.java`, `SecurityConfig.java`, etc.
  - `dto/`: Request and Response DTOs
- Web Frontend: `frontend-web/`
  - `src/api/`: Axios API clients
  - `src/components/`: Navigation, Catalog, Modals (Reference, 2FA, Receipt), History
  - `src/pages/`: LoginPage, CatalogPage, HistoryPage
  - `src/context/`: AuthContext
- Mobile Flutter: `mobile-flutter/`
  - `lib/models/`: Dart data models
  - `lib/services/`: HTTP REST clients
  - `lib/providers/`: State management providers
  - `lib/screens/`: LoginScreen, HomeScreen, PaymentFlowScreen, ReceiptScreen, HistoryScreen
  - `lib/widgets/`: UI components and design tokens
- Documentation:
  - `DOCUMENTACION_TECNICA.md`: Architectural description, design by layers, consumed services, demonstration flow.
