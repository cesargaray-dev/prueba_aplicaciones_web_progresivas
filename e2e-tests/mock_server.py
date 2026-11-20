"""
Authoritative In-Memory Contract Mock Server for Gesto Pago Payment Platform.
Implements full PROJECT.md § Interface Contracts (F1-F9) for deterministic
standalone verification of all E2E tests across Tiers 1-4.
"""

from datetime import date, datetime, timezone
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import random
import re
import string
import threading
import time
from typing import Any, Dict, List, Optional, Tuple


ALLOWED_CORS_ORIGINS = frozenset({
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://localhost:8085",
    "http://127.0.0.1:8085",
})

PLACEHOLDER_EMAIL_MARKERS = (
    "@ejemplo.com",
    ".ejemplo.com",
    "@example.com",
    ".example.com",
    "@test.com",
    ".local",
)

INVALID_PHONE_SEQUENCES = frozenset({
    "0123456789",
    "1234567890",
    "9876543210",
    "0987654321",
    "2345678901",
    "1234512345",
    "5555555555",
})

TELEPHONY_CATEGORY_IDS = frozenset({"1", "2", "3", "4", "12", "13", "19"})

NAME_REGEX = re.compile(r"^[A-Za-zÁÉÍÓÚáéíóúÑñ\s]{2,50}$")
USERNAME_REGEX = re.compile(r"^[A-Za-z0-9_.\-]{4,50}$")
PASSWORD_REGEX = re.compile(r"^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,100}$")
EMAIL_REGEX = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


def _hash_secret(raw: str) -> str:
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _mask_email(addr: str) -> str:
    if "@" not in addr:
        return "***"
    local, domain = addr.split("@", 1)
    prefix = local[:3] if len(local) >= 3 else local[:1]
    return f"{prefix}***@{domain}"


def _is_placeholder_email(addr: str) -> bool:
    lower = (addr or "").strip().lower()
    return any(marker in lower for marker in PLACEHOLDER_EMAIL_MARKERS)


def _is_valid_ift_phone(phone: str) -> bool:
    if not re.match(r"^[2-9]\d{9}$", phone or ""):
        return False
    if re.match(r"^(\d)\1{9}$", phone):
        return False
    if phone in INVALID_PHONE_SEQUENCES:
        return False
    return True


def _validate_age_18_110(dob_str: str) -> Tuple[bool, str]:
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", dob_str or ""):
        return False, "Formato de fechaNacimiento inválido (esperado yyyy-MM-dd)"
    try:
        dob = datetime.strptime(dob_str, "%Y-%m-%d").date()
    except ValueError:
        return False, "Fecha de nacimiento inválida"
    today = date.today()
    if dob > today:
        return False, "La fecha de nacimiento no puede ser futura"
    years = today.year - dob.year - ((today.month, today.day) < (dob.month, dob.day))
    if years < 18 or years > 110:
        return False, "La edad permitida para apertura de cuenta es de 18 a 110 años"
    return True, ""


def _luhn_mod10(ref: str) -> bool:
    if not ref.isdigit() or len(ref) < 2:
        return False
    total = 0
    double = False
    for ch in reversed(ref):
        d = int(ch)
        if double:
            d *= 2
            if d > 9:
                d -= 9
        total += d
        double = not double
    return total % 10 == 0


def _mod11(ref: str) -> bool:
    if not ref.isdigit() or len(ref) < 2:
        return False
    check = int(ref[-1])
    weight = 2
    total = 0
    for ch in reversed(ref[:-1]):
        total += int(ch) * weight
        weight = 2 if weight == 7 else weight + 1
    expected = 11 - (total % 11)
    if expected in (10, 11):
        expected = 0
    return expected == check


def _build_pdf_bytes(tx: Dict[str, Any]) -> bytes:
    """Builds a valid %PDF-1.4 document (>500 bytes) for receipt download tests."""
    folio = tx.get("folioTicket", "FT-MOCK-0001")
    servicio = tx.get("servicio", "Servicio")
    producto = tx.get("producto", "Producto")
    monto = tx.get("monto", 0.0)
    auth_num = tx.get("numeroAutorizacion", "AUTH000")
    ref = tx.get("referencia", "N/A")
    estado = tx.get("estado", "EXITOSA")
    fecha = tx.get("fechaOperacion", "")

    lines = [
        "BANCO DIGITAL - COMPROBANTE OFICIAL DE PAGO",
        f"Folio Ticket: {folio}",
        f"Autorizacion: {auth_num}",
        f"Servicio: {servicio} - {producto}",
        f"Referencia: {ref}",
        f"Monto Pagado: ${monto:.2f} MXN",
        f"Estado: {estado}",
        f"Fecha Operacion: {fecha}",
        "Gracias por utilizar Banco Digital GestoPago.",
    ]
    text_ops = ["BT", "/F1 11 Tf", "50 740 Td", "16 TL"]
    for idx, line in enumerate(lines):
        safe = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        if idx == 0:
            text_ops.append(f"({safe}) Tj")
        else:
            text_ops.append(f"T* ({safe}) Tj")
    text_ops.append("ET")
    stream_data = "\n".join(text_ops).encode("latin-1", errors="replace")

    objs = [
        b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
        b"2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
        b"3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n",
        (
            f"4 0 obj\n<< /Length {len(stream_data)} >>\nstream\n".encode("ascii")
            + stream_data
            + b"\nendstream\nendobj\n"
        ),
        b"5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
    ]
    header = b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n"
    offsets: List[int] = []
    body = bytearray(header)
    for obj in objs:
        offsets.append(len(body))
        body.extend(obj)
    xref_pos = len(body)
    xref = [f"xref\n0 {len(objs) + 1}\n0000000000 65535 f \n".encode("ascii")]
    for off in offsets:
        xref.append(f"{off:010d} 00000 n \n".encode("ascii"))
    trailer = (
        f"trailer\n<< /Size {len(objs) + 1} /Root 1 0 R >>\nstartxref\n{xref_pos}\n%%EOF\n"
    ).encode("ascii")
    return bytes(body) + b"".join(xref) + trailer


class MockState:
    """Thread-safe in-memory state tracking accounts, biometrics, 2FA OTPs, and transactions."""

    def __init__(self, expose_otp: bool = False):
        self.lock = threading.Lock()
        self.expose_otp = expose_otp
        self.reset(expose_otp=expose_otp)

    def reset(self, expose_otp: Optional[bool] = None) -> None:
        with getattr(self, "lock", threading.Lock()):
            if expose_otp is not None:
                self.expose_otp = expose_otp

            self.cliente_seq = 10
            self.account_seq = 5000000000000000
            self.transaction_id_counter = 200
            now_iso = datetime.now(timezone.utc).isoformat()
            older_iso = "2026-11-18T10:00:00+00:00"

            self.users: Dict[str, Dict[str, Any]] = {
                "testuser": {
                    "clienteId": 1,
                    "usuario": "testuser",
                    "password_hash": _hash_secret("CorrectP@ss123"),
                    "nombre": "Carlos",
                    "segundoNombre": "Alberto",
                    "apellidoPaterno": "Perez",
                    "apellidoMaterno": "Lopez",
                    "nombreCompleto": "Carlos Alberto Perez Lopez",
                    "email": "carlos.perez@bancodigital-e2e.mx",
                    "telefonoMovil": "5512345678",
                    "numeroCuenta": "1234567890123456",
                    "saldo": 500.00,
                    "activo": True,
                    "bloqueado": False,
                    "intentosFallidos": 0,
                    "ultimoAcceso": now_iso,
                    "biometria_habilitada": False,
                    "biometria_hash": None,
                    "biometria_intentos_fallidos": 0,
                    "biometria_bloqueada": False,
                },
                "secondary_user": {
                    "clienteId": 2,
                    "usuario": "secondary_user",
                    "password_hash": _hash_secret("SecondaryP@ss123"),
                    "nombre": "Laura",
                    "segundoNombre": "",
                    "apellidoPaterno": "Gomez",
                    "apellidoMaterno": "Diaz",
                    "nombreCompleto": "Laura Gomez Diaz",
                    "email": "laura.gomez@bancodigital-e2e.mx",
                    "telefonoMovil": "5598765432",
                    "numeroCuenta": "2222333344445555",
                    "saldo": 1000.00,
                    "activo": True,
                    "bloqueado": False,
                    "intentosFallidos": 0,
                    "ultimoAcceso": now_iso,
                    "biometria_habilitada": False,
                    "biometria_hash": None,
                    "biometria_intentos_fallidos": 0,
                    "biometria_bloqueada": False,
                },
                "rich_user": {
                    "clienteId": 3,
                    "usuario": "rich_user",
                    "password_hash": _hash_secret("RichPass123!"),
                    "nombre": "Roberto",
                    "segundoNombre": "",
                    "apellidoPaterno": "Garza",
                    "apellidoMaterno": "Soto",
                    "nombreCompleto": "Roberto Garza Soto",
                    "email": "roberto.garza@bancodigital-e2e.mx",
                    "telefonoMovil": "5587654321",
                    "numeroCuenta": "1111222233334444",
                    "saldo": 5000.00,
                    "activo": True,
                    "bloqueado": False,
                    "intentosFallidos": 0,
                    "ultimoAcceso": now_iso,
                    "biometria_habilitada": False,
                    "biometria_hash": None,
                    "biometria_intentos_fallidos": 0,
                    "biometria_bloqueada": False,
                },
                "lockout_user": {
                    "clienteId": 4,
                    "usuario": "lockout_user",
                    "password_hash": _hash_secret("TargetPass123"),
                    "nombre": "Maria",
                    "segundoNombre": "",
                    "apellidoPaterno": "Ramirez",
                    "apellidoMaterno": "Cruz",
                    "nombreCompleto": "Maria Ramirez Cruz",
                    "email": "maria.ramirez@bancodigital-e2e.mx",
                    "telefonoMovil": "5544332211",
                    "numeroCuenta": "9876543210987654",
                    "saldo": 100.00,
                    "activo": True,
                    "bloqueado": False,
                    "intentosFallidos": 0,
                    "ultimoAcceso": now_iso,
                    "biometria_habilitada": False,
                    "biometria_hash": None,
                    "biometria_intentos_fallidos": 0,
                    "biometria_bloqueada": False,
                },
            }

            self.active_sessions: Dict[str, str] = {}
            self.active_2fa: Dict[str, Dict[str, Any]] = {}
            self.idempotency_cache: Dict[Tuple, float] = {}

            seed_tx_1 = {
                "idPago": 101,
                "folioTicket": "FT-20261119-INIT1",
                "numeroAutorizacion": "AUTH000101",
                "idTxGestopago": "GP-101",
                "idServicio": 133,
                "servicio": "Telcel",
                "idProducto": 403,
                "producto": "Recarga Telcel 20",
                "telefono": "5512345678",
                "referencia": "5512345678",
                "monto": 20.00,
                "comision": 0.00,
                "saldoAnterior": 520.00,
                "saldoRestante": 500.00,
                "concepto": "Pago de Telcel - Recarga Telcel 20",
                "fechaOperacion": now_iso,
                "estado": "EXITOSA",
                "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/133.png",
            }
            seed_tx_2 = {
                "idPago": 100,
                "folioTicket": "FT-20261118-INIT2",
                "numeroAutorizacion": "AUTH000100",
                "idTxGestopago": "GP-100",
                "idServicio": 166,
                "servicio": "CFE",
                "idProducto": 597,
                "producto": "CFE Suministrador de Servicios Básicos",
                "telefono": None,
                "referencia": "010101010101010101010101010101",
                "monto": 100.00,
                "comision": 0.00,
                "saldoAnterior": 620.00,
                "saldoRestante": 520.00,
                "concepto": "Pago de CFE - CFE Suministrador de Servicios Básicos",
                "fechaOperacion": older_iso,
                "estado": "EXITOSA",
                "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/166.png",
            }
            seed_sec = {
                "idPago": 102,
                "folioTicket": "FT-20261119-SEC01",
                "numeroAutorizacion": "AUTH000102",
                "idTxGestopago": "GP-102",
                "idServicio": 133,
                "servicio": "Telcel",
                "idProducto": 403,
                "producto": "Recarga Telcel 20",
                "telefono": "5598765432",
                "referencia": "5598765432",
                "monto": 20.00,
                "comision": 0.00,
                "saldoAnterior": 1020.00,
                "saldoRestante": 1000.00,
                "concepto": "Pago de Telcel - Recarga Telcel 20",
                "fechaOperacion": now_iso,
                "estado": "EXITOSA",
                "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/133.png",
            }

            self.transaction_history: Dict[str, List[Dict[str, Any]]] = {
                "1234567890123456": [seed_tx_1, seed_tx_2],
                "2222333344445555": [seed_sec],
                "1111222233334444": [],
                "9876543210987654": [],
            }
            self.folio_owner: Dict[str, Tuple[str, Dict[str, Any]]] = {
                seed_tx_1["folioTicket"]: ("1234567890123456", seed_tx_1),
                seed_tx_2["folioTicket"]: ("1234567890123456", seed_tx_2),
                seed_sec["folioTicket"]: ("2222333344445555", seed_sec),
            }

            # Exact Java ProductDTO serialization fields
            self.catalog: List[Dict[str, Any]] = [
                {
                    "id": "403",
                    "name": "Paquete Amigo 20",
                    "service": "Telcel",
                    "serviceId": "133",
                    "price": "20.00",
                    "referenceType": "a",
                    "frontType": "1",
                    "hasVerificationDigit": "false",
                    "categoryId": "1",
                    "legend": "Ingresa tu número Telcel a 10 dígitos para recarga inmediata.",
                    "showAyuda": True,
                    "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/133.png",
                },
                {
                    "id": "404",
                    "name": "Paquete Amigo 50",
                    "service": "Telcel",
                    "serviceId": "133",
                    "price": "50.00",
                    "referenceType": "a",
                    "frontType": "1",
                    "hasVerificationDigit": "false",
                    "categoryId": "1",
                    "legend": "Recarga Telcel telefonía móvil 50 MXN.",
                    "showAyuda": True,
                    "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/133.png",
                },
                {
                    "id": "597",
                    "name": "CFE Suministrador de Servicios Básicos",
                    "service": "CFE",
                    "serviceId": "166",
                    "price": "0.00",
                    "referenceType": "bc",
                    "frontType": "2",
                    "hasVerificationDigit": "false",
                    "categoryId": "2",
                    "legend": "Ingresa contrato (10-12 dígitos) o código de barras (20-30 dígitos).",
                    "showAyuda": True,
                    "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/166.png",
                },
                {
                    "id": "598",
                    "name": "Agua y Drenaje de Monterrey",
                    "service": "Agua y Drenaje",
                    "serviceId": "167",
                    "price": "0.00",
                    "referenceType": "b",
                    "frontType": "2",
                    "hasVerificationDigit": "false",
                    "categoryId": "2",
                    "legend": "Código de barras numérico de 8 a 30 dígitos.",
                    "showAyuda": True,
                    "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/167.png",
                },
                {
                    "id": "271",
                    "name": "Telmex Telefonía e Internet",
                    "service": "Telmex",
                    "serviceId": "107",
                    "price": "0.00",
                    "referenceType": "c",
                    "frontType": "2",
                    "hasVerificationDigit": "false",
                    "categoryId": "2",
                    "legend": "Número de teléfono o contrato de 6 a 16 dígitos.",
                    "showAyuda": False,
                    "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/107.png",
                },
                {
                    "id": "272",
                    "name": "IZZI Telecomunicaciones",
                    "service": "IZZI",
                    "serviceId": "108",
                    "price": "0.00",
                    "referenceType": "a",
                    "frontType": "2",
                    "hasVerificationDigit": "false",
                    "categoryId": "2",
                    "legend": "Referencia alfanumérica de contrato.",
                    "showAyuda": False,
                    "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/108.png",
                },
                {
                    "id": "7357",
                    "name": "CFE Consulta de Saldo",
                    "service": "CFE",
                    "serviceId": "166",
                    "price": "0.00",
                    "referenceType": "bc",
                    "frontType": "4",
                    "hasVerificationDigit": "false",
                    "categoryId": "2",
                    "legend": "Producto únicamente de consulta (sin pago directo).",
                    "showAyuda": False,
                    "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/166.png",
                },
                {
                    "id": "8010",
                    "name": "Netwey Consulta",
                    "service": "Netwey",
                    "serviceId": "1161",
                    "price": "0.00",
                    "referenceType": "a",
                    "frontType": "5",
                    "hasVerificationDigit": "false",
                    "categoryId": "2",
                    "legend": "Producto informativo tipoFront 5 no pagable.",
                    "showAyuda": False,
                    "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/1161.png",
                },
                {
                    "id": "9901",
                    "name": "Servicio con Dígito Verificador",
                    "service": "Servicio DV",
                    "serviceId": "990",
                    "price": "0.00",
                    "referenceType": "b",
                    "frontType": "2",
                    "hasVerificationDigit": "true",
                    "categoryId": "2",
                    "legend": "Referencia de 8 a 30 dígitos con dígito verificador Luhn Mod-10/Mod-11.",
                    "showAyuda": True,
                    "logoUrl": "https://gestopago.portalventas.net/sistema/images/gestopago/servicios/990.png",
                },
            ]

    def get_latest_otp(self, numero_cuenta: str) -> Optional[str]:
        with self.lock:
            entry = self.active_2fa.get(numero_cuenta)
            if entry and not entry.get("used"):
                return entry.get("codigo")
            return None


GLOBAL_MOCK_STATE = MockState(expose_otp=False)


def _user_profile_dto(user: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "clienteId": user["clienteId"],
        "usuario": user["usuario"],
        "nombre": user["nombre"],
        "segundoNombre": user.get("segundoNombre", ""),
        "apellidoPaterno": user["apellidoPaterno"],
        "apellidoMaterno": user["apellidoMaterno"],
        "nombreCompleto": user["nombreCompleto"],
        "email": user["email"],
        "telefonoMovil": user["telefonoMovil"],
        "numeroCuenta": user["numeroCuenta"],
        "saldo": round(float(user["saldo"]), 2),
        "activo": user["activo"],
        "ultimoAcceso": user["ultimoAcceso"],
    }


def _login_response_dto(user: Dict[str, Any], token: str) -> Dict[str, Any]:
    return {
        "token": token,
        "usuario": user["usuario"],
        "clienteId": user["clienteId"],
        "nombreCompleto": user["nombreCompleto"],
        "email": user["email"],
        "telefonoMovil": user["telefonoMovil"],
        "numeroCuenta": user["numeroCuenta"],
        "saldo": round(float(user["saldo"]), 2),
        "ultimoAcceso": user["ultimoAcceso"],
    }


def _validate_reference_logic(product: Optional[Dict[str, Any]], referencia: str) -> Tuple[bool, str]:
    ref = (referencia or "").strip()
    if not ref or re.search(r"[\x00-\x1f<>;'\"&]", ref):
        return False, "La referencia está vacía o contiene caracteres no permitidos"

    if product is None:
        return False, "Servicio o producto no encontrado en catálogo"

    front = str(product["frontType"])
    ref_type = str(product["referenceType"]).lower()
    cat_id = str(product["categoryId"])
    has_dv = str(product.get("hasVerificationDigit", "false")).lower() == "true"

    if front in ("4", "5"):
        return False, f"Producto informativo (tipoFront={front}): pagos no permitidos"

    if front == "1" and cat_id in TELEPHONY_CATEGORY_IDS:
        if not _is_valid_ift_phone(ref):
            return False, "El número telefónico debe ser un celular mexicano IFT válido de 10 dígitos"
        return True, "Referencia telefónica válida"

    # tipoFront 2 (or non-telephony tipoFront 1)
    if ref_type == "b":
        if not re.match(r"^\d{8,30}$", ref):
            return False, "Referencia tipo 'b' debe tener de 8 a 30 dígitos"
    elif ref_type == "c":
        if not re.match(r"^\d{6,16}$", ref):
            return False, "Referencia tipo 'c' debe tener de 6 a 16 dígitos"
    elif ref_type == "bc":
        if not (re.match(r"^\d{10,12}$", ref) or re.match(r"^\d{20,30}$", ref)):
            return False, "Referencia tipo 'bc' debe tener 10-12 o 20-30 dígitos"
    else:  # 'a'
        if not re.match(r"^[a-zA-Z0-9]{4,30}$", ref):
            return False, "Referencia alfanumérica inválida"

    if has_dv and not (_luhn_mod10(ref) or _mod11(ref)):
        return False, "Dígito verificador inválido (Luhn Mod-10 / Mod-11)"

    return True, "Referencia válida"


class MockRequestHandler(BaseHTTPRequestHandler):
    """Handles HTTP requests adhering strictly to PROJECT.md § Interface Contracts."""

    def log_message(self, format: str, *args: Any) -> None:
        pass

    def _send_security_and_cors_headers(self) -> None:
        origin = self.headers.get("Origin")
        if origin and origin in ALLOWED_CORS_ORIGINS:
            self.send_header("Access-Control-Allow-Origin", origin)
            self.send_header("Vary", "Origin")
            self.send_header("Access-Control-Allow-Credentials", "true")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept")
        self.send_header("Access-Control-Max-Age", "3600")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")

    def _send_json(self, status_code: int, data: Any) -> None:
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self._send_security_and_cors_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_pdf(self, pdf_bytes: bytes, folio: str) -> None:
        self.send_response(200)
        self._send_security_and_cors_headers()
        self.send_header("Content-Type", "application/pdf")
        self.send_header("Content-Disposition", f'attachment; filename="comprobante-{folio}.pdf"')
        self.send_header("Content-Length", str(len(pdf_bytes)))
        self.end_headers()
        self.wfile.write(pdf_bytes)

    def _read_json_body(self) -> Tuple[bool, Optional[Dict[str, Any]]]:
        content_length = int(self.headers.get("Content-Length", 0))
        if content_length == 0:
            return True, None
        raw = self.rfile.read(content_length)
        try:
            parsed = json.loads(raw.decode("utf-8"))
            if not isinstance(parsed, dict):
                return False, None
            return True, parsed
        except Exception:
            return False, None

    def _get_bearer_token(self) -> Optional[str]:
        auth_header = self.headers.get("Authorization", "")
        if auth_header.startswith("Bearer "):
            tok = auth_header[7:].strip()
            return tok if tok else None
        return None

    def _authenticate(self) -> Optional[Dict[str, Any]]:
        """Strict Bearer authentication; ignores ?usuario= query parameters."""
        token = self._get_bearer_token()
        if not token:
            self._send_json(401, {"codigo": "AUTH_401", "mensaje": "Token Bearer requerido"})
            return None
        state = GLOBAL_MOCK_STATE
        with state.lock:
            usuario = state.active_sessions.get(token)
            if not usuario or usuario not in state.users:
                self._send_json(401, {"codigo": "AUTH_02", "mensaje": "Sesión inválida o expirada"})
                return None
            return state.users[usuario]

    def _find_product(self, id_servicio: Any, id_producto: Any) -> Optional[Dict[str, Any]]:
        state = GLOBAL_MOCK_STATE
        sid = str(id_servicio) if id_servicio is not None else None
        pid = str(id_producto) if id_producto is not None else None
        if pid:
            found = next((p for p in state.catalog if p["id"] == pid and (not sid or p["serviceId"] == sid)), None)
            if found:
                return found
        if sid:
            return next((p for p in state.catalog if p["serviceId"] == sid), None)
        return None

    def do_OPTIONS(self) -> None:
        self.send_response(204)
        self._send_security_and_cors_headers()
        self.end_headers()

    def do_GET(self) -> None:
        path, _, _ = self.path.partition("?")
        state = GLOBAL_MOCK_STATE

        if path == "/actuator/health":
            self._send_json(200, {"status": "UP"})
            return

        # Mock-only out-of-band inbox (never exists in live backend)
        if path.startswith("/__mock/inbox/") and path.endswith("/latest-otp"):
            parts = path.split("/")
            if len(parts) == 5:
                cuenta = parts[3]
                code = state.get_latest_otp(cuenta)
                if code:
                    self._send_json(200, {"numeroCuenta": cuenta, "codigo2FA": code})
                else:
                    self._send_json(404, {"mensaje": "No active OTP in mock inbox"})
                return

        if path == "/api/v1/auth/me":
            user = self._authenticate()
            if not user:
                return
            self._send_json(200, _user_profile_dto(user))
            return

        if path == "/api/v1/auth/biometria/estado":
            user = self._authenticate()
            if not user:
                return
            self._send_json(200, {
                "habilitada": bool(user.get("biometria_habilitada", False)),
                "usuario": user["usuario"],
                "tipo": "HUELLA",
                "mensaje": "Estado biométrico consultado",
            })
            return

        if path == "/api/v1/gestopago/productos":
            self._send_json(200, {
                "status": "OK",
                "message": "Catálogo obtenido",
                "codigoCache": "HIT",
                "origenCache": "GESTOPAGO",
                "data": state.catalog,
            })
            return

        if path == "/api/v1/gestopago/productos/agrupados":
            grouped: Dict[str, List[Dict[str, Any]]] = {}
            for item in state.catalog:
                grouped.setdefault(item["frontType"], []).append(item)
            groups = [
                {"tipoFront": front, "total": len(items), "productos": items}
                for front, items in sorted(grouped.items(), key=lambda kv: int(kv[0]))
            ]
            self._send_json(200, {
                "status": "OK",
                "message": "Catálogo agrupado obtenido",
                "codigoCache": "HIT",
                "origenCache": "GESTOPAGO",
                "totalProductos": len(state.catalog),
                "totalGrupos": len(groups),
                "grupos": groups,
            })
            return

        if path.startswith("/api/v1/pagos/comprobante/") and path.endswith("/pdf"):
            user = self._authenticate()
            if not user:
                return
            parts = path.split("/")
            if len(parts) == 7:
                folio = parts[5]
                with state.lock:
                    owner_entry = state.folio_owner.get(folio)
                if not owner_entry:
                    self._send_json(404, {"codigo": "FOLIO_404", "mensaje": "Comprobante no encontrado"})
                    return
                owner_cuenta, tx = owner_entry
                if owner_cuenta != user["numeroCuenta"]:
                    self._send_json(403, {"codigo": "FORBIDDEN", "mensaje": "No tienes permiso para acceder a este comprobante"})
                    return
                self._send_pdf(_build_pdf_bytes(tx), folio)
                return

        if path.startswith("/api/v1/clientes") or path.startswith("/api/v1/cuentas") or path.startswith("/personas"):
            user = self._authenticate()
            if not user:
                return
            self._send_json(200, {"data": [], "usuario": user["usuario"]})
            return

        self._send_json(404, {"error": "Not Found", "path": path})

    def do_POST(self) -> None:
        path, _, _ = self.path.partition("?")
        ok, body = self._read_json_body()
        if not ok:
            self._send_json(400, {"codigo": "BAD_JSON", "mensaje": "JSON inválido"})
            return
        state = GLOBAL_MOCK_STATE

        # Legacy / Admin auth enforcement
        if path.startswith("/api/v1/clientes") or path.startswith("/api/v1/cuentas") or path.startswith("/personas"):
            user = self._authenticate()
            if not user:
                return
            self._send_json(200, {"codigo": 0, "mensaje": "Operación ejecutada"})
            return

        # 1. POST /api/v1/auth/login
        if path == "/api/v1/auth/login":
            if not body or not body.get("usuario") or not body.get("contrasena"):
                self._send_json(400, {"codigo": "AUTH_BAD_REQ", "mensaje": "Usuario y contraseña requeridos"})
                return
            usuario = str(body["usuario"]).strip()
            raw_pass = str(body["contrasena"])
            with state.lock:
                user = state.users.get(usuario)
                if not user:
                    self._send_json(401, {"codigo": "AUTH_01", "mensaje": "Credenciales inválidas"})
                    return
                if user.get("bloqueado"):
                    self._send_json(401, {"codigo": "AUTH_LOCKED", "mensaje": "Cuenta bloqueada por múltiples intentos fallidos"})
                    return
                if _hash_secret(raw_pass) != user["password_hash"]:
                    user["intentosFallidos"] += 1
                    if user["intentosFallidos"] >= 5:
                        user["bloqueado"] = True
                        self._send_json(401, {"codigo": "AUTH_LOCKED", "mensaje": "Cuenta bloqueada por múltiples intentos fallidos"})
                        return
                    self._send_json(401, {"codigo": "AUTH_01", "mensaje": "Credenciales inválidas"})
                    return
                user["intentosFallidos"] = 0
                user["ultimoAcceso"] = datetime.now(timezone.utc).isoformat()
                tok = f"jwt-{usuario}-{int(time.time() * 1000)}-{random.randint(1000,9999)}"
                state.active_sessions[tok] = usuario
                self._send_json(200, _login_response_dto(user, tok))
            return

        # 2. POST /api/v1/auth/register
        if path == "/api/v1/auth/register":
            if not body:
                self._send_json(400, {"codigo": "REG_BAD", "mensaje": "Payload requerido"})
                return
            nombre = str(body.get("nombre", "")).strip()
            segundo = str(body.get("segundoNombre", "") or "").strip()
            ap_pat = str(body.get("apellidoPaterno", "")).strip()
            ap_mat = str(body.get("apellidoMaterno", "")).strip()
            dob = str(body.get("fechaNacimiento", "")).strip()
            sexo = str(body.get("sexo", "HOMBRE")).strip()
            correo = str(body.get("correo", "")).strip()
            tel = str(body.get("telefonoMovil", "")).strip()
            usuario = str(body.get("usuario", "")).strip()
            contrasena = str(body.get("contrasena", ""))

            if not (NAME_REGEX.match(nombre) and NAME_REGEX.match(ap_pat) and NAME_REGEX.match(ap_mat)):
                self._send_json(400, {"codigo": "REG_NAME", "mensaje": "Nombre y apellidos deben contener solo letras (2-50)"})
                return
            if segundo and not NAME_REGEX.match(segundo):
                self._send_json(400, {"codigo": "REG_NAME2", "mensaje": "Segundo nombre inválido"})
                return
            age_ok, age_msg = _validate_age_18_110(dob)
            if not age_ok:
                self._send_json(400, {"codigo": "REG_AGE", "mensaje": age_msg})
                return
            if sexo not in ("HOMBRE", "MUJER"):
                self._send_json(400, {"codigo": "REG_SEXO", "mensaje": "Sexo inválido"})
                return
            if not EMAIL_REGEX.match(correo) or _is_placeholder_email(correo):
                self._send_json(400, {"codigo": "REG_EMAIL", "mensaje": "Correo electrónico inválido o placeholder no permitido"})
                return
            if not _is_valid_ift_phone(tel):
                self._send_json(400, {"codigo": "REG_PHONE", "mensaje": "Teléfono móvil IFT mexicano de 10 dígitos inválido"})
                return
            if not USERNAME_REGEX.match(usuario):
                self._send_json(400, {"codigo": "REG_USER", "mensaje": "Usuario inválido (4-50 caracteres)"})
                return
            if not PASSWORD_REGEX.match(contrasena):
                self._send_json(400, {"codigo": "REG_PASS", "mensaje": "Contraseña débil (mín. 8 chars, mayúscula, minúscula y dígito)"})
                return

            with state.lock:
                if usuario in state.users or any(u["email"].lower() == correo.lower() for u in state.users.values()):
                    self._send_json(409, {"codigo": "REG_CONFLICT", "mensaje": "El usuario o correo ya está registrado"})
                    return
                state.cliente_seq += 1
                state.account_seq += 1
                num_cuenta = f"{state.account_seq:016d}"
                full_name = " ".join(p for p in (nombre, segundo, ap_pat, ap_mat) if p)
                now_iso = datetime.now(timezone.utc).isoformat()
                new_user = {
                    "clienteId": state.cliente_seq,
                    "usuario": usuario,
                    "password_hash": _hash_secret(contrasena),
                    "nombre": nombre,
                    "segundoNombre": segundo,
                    "apellidoPaterno": ap_pat,
                    "apellidoMaterno": ap_mat,
                    "nombreCompleto": full_name,
                    "email": correo,
                    "telefonoMovil": tel,
                    "numeroCuenta": num_cuenta,
                    "saldo": 200.00,
                    "activo": True,
                    "bloqueado": False,
                    "intentosFallidos": 0,
                    "ultimoAcceso": now_iso,
                    "biometria_habilitada": False,
                    "biometria_hash": None,
                    "biometria_intentos_fallidos": 0,
                    "biometria_bloqueada": False,
                }
                state.users[usuario] = new_user
                state.transaction_history[num_cuenta] = []
                tok = f"jwt-{usuario}-{int(time.time() * 1000)}-{random.randint(1000,9999)}"
                state.active_sessions[tok] = usuario
                self._send_json(201, _login_response_dto(new_user, tok))
            return

        # 3. POST /api/v1/auth/logout
        if path == "/api/v1/auth/logout":
            user = self._authenticate()
            if not user:
                return
            tok = self._get_bearer_token()
            with state.lock:
                if tok and tok in state.active_sessions:
                    del state.active_sessions[tok]
            self._send_json(200, {"codigo": 0, "mensaje": "Sesión cerrada exitosamente"})
            return

        # 4. POST /api/v1/auth/me/abonar
        if path == "/api/v1/auth/me/abonar":
            user = self._authenticate()
            if not user:
                return
            if not body or "monto" not in body:
                self._send_json(400, {"codigo": "ABONO_BAD", "mensaje": "Monto requerido"})
                return
            monto_raw = body["monto"]
            if isinstance(monto_raw, bool) or not isinstance(monto_raw, (int, float)):
                self._send_json(400, {"codigo": "TYPE_ERROR", "mensaje": "El campo monto debe ser numérico"})
                return
            monto = float(monto_raw)
            if monto < 1.00 or monto > 50000.00:
                self._send_json(400, {"codigo": "ABONO_RANGE", "mensaje": "El monto de abono debe estar entre $1.00 y $50,000.00"})
                return
            with state.lock:
                user["saldo"] = round(float(user["saldo"]) + monto, 2)
                self._send_json(200, _user_profile_dto(user))
            return

        # 5. POST /api/v1/auth/biometria/registrar
        if path == "/api/v1/auth/biometria/registrar":
            user = self._authenticate()
            if not user:
                return
            if not body or "tokenBiometrico" not in body:
                self._send_json(400, {"codigo": "BIO_REQ", "mensaje": "tokenBiometrico requerido"})
                return
            bio_tok = str(body.get("tokenBiometrico", ""))
            if len(bio_tok) < 32 or len(bio_tok) > 256:
                self._send_json(400, {"codigo": "BIO_LEN", "mensaje": "tokenBiometrico debe tener entre 32 y 256 caracteres"})
                return
            with state.lock:
                user["biometria_habilitada"] = True
                user["biometria_hash"] = _hash_secret(bio_tok)
                user["biometria_intentos_fallidos"] = 0
                user["biometria_bloqueada"] = False
            self._send_json(200, {
                "habilitada": True,
                "usuario": user["usuario"],
                "tipo": "HUELLA",
                "mensaje": "Huella digital registrada correctamente",
            })
            return

        # 6. POST /api/v1/auth/login/biometrico
        if path == "/api/v1/auth/login/biometrico":
            if not body or not body.get("usuario") or not body.get("tokenBiometrico"):
                self._send_json(400, {"codigo": "BIO_REQ", "mensaje": "Usuario y tokenBiometrico requeridos"})
                return
            usuario = str(body["usuario"]).strip()
            bio_tok = str(body["tokenBiometrico"])
            if len(bio_tok) < 32 or len(bio_tok) > 256:
                self._send_json(400, {"codigo": "BIO_LEN", "mensaje": "tokenBiometrico inválido"})
                return
            with state.lock:
                user = state.users.get(usuario)
                if not user or not user.get("biometria_habilitada") or not user.get("biometria_hash"):
                    self._send_json(401, {"codigo": "BIO_UNAUTH", "mensaje": "Biometría no habilitada para este usuario"})
                    return
                if user.get("biometria_bloqueada"):
                    self._send_json(401, {"codigo": "BIO_LOCKED", "mensaje": "Acceso biométrico bloqueado por múltiples intentos fallidos"})
                    return
                if _hash_secret(bio_tok) != user["biometria_hash"]:
                    user["biometria_intentos_fallidos"] = user.get("biometria_intentos_fallidos", 0) + 1
                    if user["biometria_intentos_fallidos"] >= 5:
                        user["biometria_bloqueada"] = True
                    self._send_json(401, {"codigo": "BIO_INVALID", "mensaje": "Token biométrico inválido"})
                    return
                user["biometria_intentos_fallidos"] = 0
                user["ultimoAcceso"] = datetime.now(timezone.utc).isoformat()
                tok = f"jwt-bio-{usuario}-{int(time.time() * 1000)}-{random.randint(1000,9999)}"
                state.active_sessions[tok] = usuario
                self._send_json(200, _login_response_dto(user, tok))
            return

        # 7. POST /api/v1/pagos/validar-referencia (supports optional auth or authenticated)
        if path == "/api/v1/pagos/validar-referencia":
            if not body or body.get("idServicio") is None:
                self._send_json(400, {"esValida": False, "mensaje": "idServicio y referencia requeridos"})
                return
            product = self._find_product(body.get("idServicio"), body.get("idProducto"))
            referencia = str(body.get("referencia", "")).strip()
            es_valida, msg = _validate_reference_logic(product, referencia)
            front_int = int(product["frontType"]) if product else 1
            ref_type = product["referenceType"] if product else "a"
            price_val = float(product["price"]) if product else 0.0
            self._send_json(200, {
                "esValida": es_valida,
                "mensaje": msg,
                "tipoFront": front_int,
                "tipoReferencia": ref_type,
                "requiereMonto": front_int != 1 or price_val <= 0.0,
                "montoSugerido": price_val if price_val > 0 else 0.0,
                "servicio": product["service"] if product else "",
                "producto": product["name"] if product else "",
                "hasVerificationDigit": product.get("hasVerificationDigit", "false") if product else "false",
            })
            return

        # 8. POST /api/v1/pagos/2fa/generar
        if path == "/api/v1/pagos/2fa/generar":
            user = self._authenticate()
            if not user:
                return
            if not body or not body.get("numeroCuenta"):
                self._send_json(400, {"codigo": "PAGO_01", "mensaje": "Número de cuenta requerido"})
                return
            num_cuenta = str(body["numeroCuenta"]).strip()
            if not re.match(r"^\d{16}$", num_cuenta):
                self._send_json(400, {"codigo": "CTA_FORMAT", "mensaje": "Número de cuenta debe tener 16 dígitos"})
                return
            with state.lock:
                target_user = next((u for u in state.users.values() if u["numeroCuenta"] == num_cuenta), None)
                if not target_user:
                    self._send_json(404, {"codigo": "CTA_404", "mensaje": "Cuenta bancaria no encontrada"})
                    return
                if num_cuenta != user["numeroCuenta"]:
                    self._send_json(403, {"codigo": "FORBIDDEN", "mensaje": "No tienes acceso a generar 2FA para otra cuenta"})
                    return

                code = f"{random.randint(100000, 999999)}"
                expires_at = time.time() + 300.0
                iso_expires = datetime.now(timezone.utc).isoformat()
                msg_id = f"<brevo-mock-{int(time.time() * 1000)}@bancodigital>"
                state.active_2fa[num_cuenta] = {
                    "codigo": code,
                    "expires_at": expires_at,
                    "used": False,
                }
                payload: Dict[str, Any] = {
                    "codigo": "0",
                    "mensaje": "Código 2FA enviado a tu correo electrónico",
                    "numeroCuenta": num_cuenta,
                    "vigenciaSegundos": 300,
                    "expiraEnSegundos": 300,
                    "fechaExpiracion": iso_expires,
                    "correoEnviado": True,
                    "correoDestino": _mask_email(user["email"]),
                    "mensajeCorreo": "Enviado vía Brevo",
                    "messageId": msg_id,
                    "brevoMessageId": msg_id,
                }
                if state.expose_otp:
                    payload["codigo2FA"] = code
                self._send_json(200, payload)
            return

        # 9. POST /api/v1/pagos/servicios
        if path == "/api/v1/pagos/servicios":
            user = self._authenticate()
            if not user:
                return
            if not body:
                self._send_json(400, {"codigo": "PAGO_BAD_REQ", "mensaje": "Payload requerido"})
                return

            num_cuenta = str(body.get("numeroCuenta", "")).strip()
            if not re.match(r"^\d{16}$", num_cuenta):
                self._send_json(400, {"codigo": "CTA_FORMAT", "mensaje": "Número de cuenta inválido"})
                return
            if num_cuenta != user["numeroCuenta"]:
                self._send_json(403, {"codigo": "FORBIDDEN", "mensaje": "No autorizado para operar sobre otra cuenta"})
                return

            monto_raw = body.get("monto")
            if isinstance(monto_raw, bool) or not isinstance(monto_raw, (int, float)):
                self._send_json(400, {"codigo": "TYPE_ERROR", "mensaje": "El monto debe ser un número JSON válido"})
                return
            monto = float(monto_raw)
            if monto <= 0.0 or monto > 50000.00 or abs(round(monto * 100) - monto * 100) > 1e-5:
                self._send_json(400, {"codigo": "MONTO_INVALIDO", "mensaje": "Monto fuera de rango o más de 2 decimales"})
                return

            codigo_2fa = str(body.get("codigo2FA", "")).strip()
            if not re.match(r"^\d{6}$", codigo_2fa):
                self._send_json(400, {"codigo": "2FA_FORMAT", "mensaje": "codigo2FA debe ser de exactamente 6 dígitos"})
                return

            id_servicio = body.get("idServicio")
            id_producto = body.get("idProducto")
            referencia = str(body.get("referencia", "") or body.get("telefono", "")).strip()
            product = self._find_product(id_servicio, id_producto)

            # Fault-injection triggers for RECHAZADA and PENDIENTE (after auth & ownership & basic format)
            is_reject_trigger = referencia.upper().startswith("RECHAZAR")
            is_timeout_trigger = referencia.upper().startswith("TIMEOUT")

            with state.lock:
                # Verify 2FA OTP first (single use)
                otp_data = state.active_2fa.get(num_cuenta)
                if not otp_data or otp_data.get("used") or time.time() > otp_data.get("expires_at", 0):
                    self._send_json(401, {"codigo": "2FA_EXPIRED", "mensaje": "Código 2FA inválido, expirado o ya utilizado"})
                    return
                if otp_data["codigo"] != codigo_2fa:
                    self._send_json(401, {"codigo": "2FA_INVALID", "mensaje": "Código 2FA incorrecto"})
                    return

                # Server-side reference validation (skip only for explicit fault triggers)
                if not is_reject_trigger and not is_timeout_trigger:
                    ref_ok, ref_msg = _validate_reference_logic(product, referencia)
                    if not ref_ok:
                        self._send_json(400, {
                            "codigo": "REF_INVALIDA",
                            "mensaje": ref_msg,
                            "estadoOperacion": "RECHAZADA",
                        })
                        return

                # Product price rules: fixed-price anti-tamper vs $10.00 open-amount floor
                if product:
                    cat_price = float(product["price"])
                    if product["frontType"] == "1" and cat_price > 0:
                        if abs(cat_price - monto) > 0.01:
                            self._send_json(400, {
                                "codigo": "PRECIO_ALTERADO",
                                "mensaje": f"El monto ({monto}) no coincide con el precio de catálogo ({cat_price})",
                            })
                            return
                    elif cat_price == 0.0 and monto < 10.00:
                        self._send_json(400, {
                            "codigo": "MONTO_MINIMO",
                            "mensaje": "El monto mínimo para pagos de servicio abierto es de $10.00 MXN",
                        })
                        return

                if user["saldo"] < monto:
                    self._send_json(400, {
                        "codigo": "SALDO_INSUFICIENTE",
                        "mensaje": "Saldo insuficiente para realizar el pago",
                        "estadoOperacion": "RECHAZADA",
                    })
                    return

                idemp_key = (num_cuenta, str(id_servicio), str(id_producto), round(monto, 2), referencia)
                last_ts = state.idempotency_cache.get(idemp_key, 0.0)
                if time.time() - last_ts < 120.0:
                    self._send_json(409, {
                        "codigo": "PAGO_DUPLICADO",
                        "mensaje": "Transacción duplicada detectada dentro de la ventana de 2 minutos",
                        "estadoOperacion": "RECHAZADA",
                    })
                    return

                # Consume OTP
                otp_data["used"] = True

                service_name = product["service"] if product else "Servicio"
                product_name = product["name"] if product else "Pago de Servicio"
                logo_url = product["logoUrl"] if product else f"https://gestopago.portalventas.net/sistema/images/gestopago/servicios/{id_servicio}.png"

                state.transaction_id_counter += 1
                tx_id = state.transaction_id_counter
                now_dt = datetime.now(timezone.utc)
                now_str = now_dt.strftime("%Y%m%d%H%M%S")
                rand_sfx = "".join(random.choices(string.ascii_uppercase + string.digits, k=4))
                folio = f"FT-{now_str[:8]}-{rand_sfx}"
                upc = f"UPC{tx_id:08d}"

                if is_reject_trigger:
                    self._send_json(422, {
                        "codigo": "51",
                        "mensaje": "Operación rechazada por el proveedor",
                        "estadoOperacion": "RECHAZADA",
                        "codigoProveedor": "51",
                        "mensajeOperacion": "Transacción rechazada por el proveedor emisor",
                        "idTransaccion": tx_id,
                        "folioTicket": folio,
                        "saldoRestanteCuenta": round(float(user["saldo"]), 2),
                        "servicio": service_name,
                        "producto": product_name,
                        "logoUrl": logo_url,
                    })
                    return

                if is_timeout_trigger:
                    self._send_json(504, {
                        "codigo": "82",
                        "mensaje": "Operación pendiente de confirmación por timeout del proveedor",
                        "estadoOperacion": "PENDIENTE",
                        "codigoProveedor": "82",
                        "mensajeOperacion": "Tiempo de espera agotado; pendiente de conciliación",
                        "idTransaccion": tx_id,
                        "folioTicket": folio,
                        "upc": upc,
                        "saldoRestanteCuenta": round(float(user["saldo"]), 2),
                        "servicio": service_name,
                        "producto": product_name,
                        "logoUrl": logo_url,
                    })
                    return

                state.idempotency_cache[idemp_key] = time.time()
                saldo_anterior = round(float(user["saldo"]), 2)
                user["saldo"] = round(saldo_anterior - monto, 2)
                saldo_restante = user["saldo"]

                auth_code = f"AUTH{tx_id:06d}"
                brevo_id = f"<brevo-rcpt-{tx_id}@bancodigital>"
                tx_record = {
                    "idPago": tx_id,
                    "folioTicket": folio,
                    "numeroAutorizacion": auth_code,
                    "idTxGestopago": f"GP-{tx_id}",
                    "idServicio": int(id_servicio),
                    "servicio": service_name,
                    "idProducto": int(id_producto),
                    "producto": product_name,
                    "telefono": str(body.get("telefono") or "") or None,
                    "referencia": referencia,
                    "monto": round(monto, 2),
                    "comision": 0.00,
                    "saldoAnterior": saldo_anterior,
                    "saldoRestante": saldo_restante,
                    "concepto": f"Pago de {service_name} - {product_name}",
                    "fechaOperacion": now_dt.isoformat(),
                    "estado": "EXITOSA",
                    "logoUrl": logo_url,
                }
                state.transaction_history.setdefault(num_cuenta, []).insert(0, tx_record)
                state.folio_owner[folio] = (num_cuenta, tx_record)

                self._send_json(200, {
                    "codigo": "0",
                    "mensaje": "Pago aplicado exitosamente",
                    "estadoOperacion": "EXITOSA",
                    "codigoProveedor": "01",
                    "mensajeOperacion": "Transacción aprobada",
                    "idTransaccion": tx_id,
                    "numeroAutorizacion": auth_code,
                    "folioTicket": folio,
                    "upc": upc,
                    "montoPagado": round(monto, 2),
                    "comision": 0.00,
                    "saldoRestanteCuenta": saldo_restante,
                    "fechaHoraOperacion": tx_record["fechaOperacion"],
                    "concepto": tx_record["concepto"],
                    "servicio": service_name,
                    "producto": product_name,
                    "logoUrl": logo_url,
                    "correoEnviado": True,
                    "correoDestino": _mask_email(user["email"]),
                    "messageId": brevo_id,
                    "brevoMessageId": brevo_id,
                })
            return

        # 10. POST /api/v1/pagos/historial
        if path == "/api/v1/pagos/historial":
            user = self._authenticate()
            if not user:
                return
            if not body or "numeroCuenta" not in body:
                self._send_json(400, {"codigo": "CTA_REQ", "mensaje": "Número de cuenta requerido"})
                return
            num_cuenta = str(body["numeroCuenta"]).strip()
            if not re.match(r"^\d{16}$", num_cuenta):
                self._send_json(400, {"codigo": "CTA_FORMAT", "mensaje": "El número de cuenta debe contener 16 dígitos numéricos"})
                return
            with state.lock:
                target = next((u for u in state.users.values() if u["numeroCuenta"] == num_cuenta), None)
                if not target:
                    self._send_json(404, {"codigo": "CTA_404", "mensaje": "Cuenta no encontrada"})
                    return
                if num_cuenta != user["numeroCuenta"]:
                    self._send_json(403, {"codigo": "FORBIDDEN", "mensaje": "No autorizado para consultar historial de otra cuenta"})
                    return
                records = sorted(
                    state.transaction_history.get(num_cuenta, []),
                    key=lambda item: item.get("fechaOperacion", ""),
                    reverse=True,
                )
                self._send_json(200, {
                    "codigo": "0",
                    "mensaje": "Historial obtenido",
                    "numeroCuenta": num_cuenta,
                    "totalMovimientos": len(records),
                    "pagos": records,
                })
            return

        # 11. POST /api/v1/pagos/comprobante/reenviar
        if path == "/api/v1/pagos/comprobante/reenviar":
            user = self._authenticate()
            if not user:
                return
            if not body or not body.get("numeroCuenta") or not body.get("folioTicket"):
                self._send_json(400, {"codigo": "RCPT_REQ", "mensaje": "numeroCuenta y folioTicket requeridos"})
                return
            num_cuenta = str(body["numeroCuenta"]).strip()
            folio = str(body["folioTicket"]).strip()
            correo_override = body.get("correoDestino")

            if num_cuenta != user["numeroCuenta"]:
                self._send_json(403, {"codigo": "FORBIDDEN", "mensaje": "No autorizado para reenviar comprobantes de otra cuenta"})
                return

            dest_email = str(correo_override).strip() if correo_override else user["email"]
            if not EMAIL_REGEX.match(dest_email) or _is_placeholder_email(dest_email):
                self._send_json(400, {"codigo": "EMAIL_PLACEHOLDER", "mensaje": "Correo destino inválido o placeholder no permitido"})
                return

            with state.lock:
                owner_entry = state.folio_owner.get(folio)
                if not owner_entry or owner_entry[0] != num_cuenta:
                    self._send_json(404, {"codigo": "FOLIO_404", "mensaje": "Folio no encontrado en la cuenta"})
                    return

            msg_id = f"<brevo-resend-{int(time.time() * 1000)}@bancodigital>"
            self._send_json(200, {
                "enviado": True,
                "correoDestino": _mask_email(dest_email),
                "messageId": msg_id,
                "mensaje": "Comprobante HTML y PDF reenviado exitosamente",
                "correoPlaceholder": False,
            })
            return

        self._send_json(404, {"error": "Not Found", "path": path})

    def do_PUT(self) -> None:
        path, _, _ = self.path.partition("?")
        ok, body = self._read_json_body()
        if not ok:
            self._send_json(400, {"codigo": "BAD_JSON", "mensaje": "JSON inválido"})
            return
        state = GLOBAL_MOCK_STATE

        if path in ("/api/v1/auth/me/perfil", "/api/v1/auth/me/contacto"):
            user = self._authenticate()
            if not user:
                return
            if not body:
                self._send_json(400, {"codigo": "PERFIL_REQ", "mensaje": "Datos requeridos"})
                return
            correo = str(body.get("correo", user["email"])).strip()
            tel = str(body.get("telefonoMovil", user["telefonoMovil"])).strip()
            if not EMAIL_REGEX.match(correo) or _is_placeholder_email(correo):
                self._send_json(400, {"codigo": "EMAIL_BAD", "mensaje": "Correo inválido o placeholder"})
                return
            if not _is_valid_ift_phone(tel):
                self._send_json(400, {"codigo": "PHONE_BAD", "mensaje": "Teléfono móvil IFT inválido"})
                return

            nombre = str(body.get("nombre", user["nombre"])).strip()
            segundo = str(body.get("segundoNombre", user.get("segundoNombre", "")) or "").strip()
            ap_pat = str(body.get("apellidoPaterno", user["apellidoPaterno"])).strip()
            ap_mat = str(body.get("apellidoMaterno", user["apellidoMaterno"])).strip()

            if not (NAME_REGEX.match(nombre) and NAME_REGEX.match(ap_pat) and NAME_REGEX.match(ap_mat)):
                self._send_json(400, {"codigo": "NAME_BAD", "mensaje": "Nombres deben contener solo letras"})
                return
            if segundo and not NAME_REGEX.match(segundo):
                self._send_json(400, {"codigo": "NAME2_BAD", "mensaje": "Segundo nombre inválido"})
                return

            with state.lock:
                user["nombre"] = nombre
                user["segundoNombre"] = segundo
                user["apellidoPaterno"] = ap_pat
                user["apellidoMaterno"] = ap_mat
                user["nombreCompleto"] = " ".join(p for p in (nombre, segundo, ap_pat, ap_mat) if p)
                user["email"] = correo
                user["telefonoMovil"] = tel
                self._send_json(200, _user_profile_dto(user))
            return

        if path == "/api/v1/auth/me/contrasena":
            user = self._authenticate()
            if not user:
                return
            if not body or not body.get("contrasenaActual") or not body.get("contrasenaNueva"):
                self._send_json(400, {"codigo": "PASS_REQ", "mensaje": "Contraseña actual y nueva requeridas"})
                return
            actual = str(body["contrasenaActual"])
            nueva = str(body["contrasenaNueva"])
            with state.lock:
                if _hash_secret(actual) != user["password_hash"]:
                    self._send_json(401, {"codigo": "PASS_WRONG", "mensaje": "La contraseña actual es incorrecta"})
                    return
                if actual == nueva or not PASSWORD_REGEX.match(nueva):
                    self._send_json(400, {"codigo": "PASS_WEAK", "mensaje": "La nueva contraseña no cumple requisitos o es igual a la actual"})
                    return
                user["password_hash"] = _hash_secret(nueva)
                # Invalidate ALL active sessions for this user (F3)
                usuario = user["usuario"]
                state.active_sessions = {
                    tok: u for tok, u in state.active_sessions.items() if u != usuario
                }
            self._send_json(200, {"codigo": 0, "mensaje": "Contraseña actualizada y sesiones anteriores cerradas"})
            return

        if path.startswith("/api/v1/clientes") or path.startswith("/personas"):
            user = self._authenticate()
            if not user:
                return
            self._send_json(200, {"codigo": 0, "mensaje": "Actualizado"})
            return

        self._send_json(404, {"error": "Not Found", "path": path})

    def do_DELETE(self) -> None:
        path, _, _ = self.path.partition("?")
        state = GLOBAL_MOCK_STATE
        if path == "/api/v1/auth/biometria":
            user = self._authenticate()
            if not user:
                return
            with state.lock:
                user["biometria_habilitada"] = False
                user["biometria_hash"] = None
                user["biometria_intentos_fallidos"] = 0
                user["biometria_bloqueada"] = False
            self._send_json(200, {
                "habilitada": False,
                "usuario": user["usuario"],
                "tipo": "HUELLA",
                "mensaje": "Biometría revocada correctamente",
            })
            return
        self._send_json(404, {"error": "Not Found", "path": path})

    def do_PATCH(self) -> None:
        path, _, _ = self.path.partition("?")
        if path.startswith("/api/v1/clientes"):
            user = self._authenticate()
            if not user:
                return
            self._send_json(200, {"codigo": 0, "mensaje": "Actualizado"})
            return
        self._send_json(404, {"error": "Not Found", "path": path})


class MockServerManager:
    """Controls the background lifecycle of the mock contract server."""

    def __init__(self, port: int = 10000, expose_otp: bool = False):
        self.port = port
        self.expose_otp = expose_otp
        self.server: Optional[ThreadingHTTPServer] = None
        self.thread: Optional[threading.Thread] = None

    def start(self) -> None:
        GLOBAL_MOCK_STATE.reset(expose_otp=self.expose_otp)
        self.server = ThreadingHTTPServer(("127.0.0.1", self.port), MockRequestHandler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def stop(self) -> None:
        if self.server:
            self.server.shutdown()
            self.server.server_close()
            self.server = None
