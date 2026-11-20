"""
Centralized configuration, account-safety guards, ephemeral user factory,
dynamic catalog lookup, and OTP resolution for the Gesto Pago E2E Suite.
"""

from dataclasses import dataclass
import imaplib
import email as email_pkg
import os
import random
import re
import string
import time
from typing import Any, Dict, List, Optional, Tuple


# Shared verified live accounts in Neon PostgreSQL — NEVER mutate credentials,
# lock out, change password, edit profile, or revoke biometrics on these!
PROTECTED_ACCOUNTS = frozenset({
    "e2ecesar1114",
    "e2efinal20261114002928",
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


@dataclass
class E2EConfig:
    base_url: str = "http://localhost:10000"
    is_mock: bool = False
    username: str = "e2ecesar1114"
    password: str = "Venta2026#"
    secondary_username: str = "e2efinal20261114002928"
    secondary_password: str = "Venta2026#"
    otp: Optional[str] = None
    imap_host: Optional[str] = None
    imap_port: int = 993
    imap_user: Optional[str] = None
    imap_pass: Optional[str] = None
    live_safe: bool = False
    allow_live_payments: bool = False
    test_email_domain: str = "bancodigital-e2e.mx"
    timeout: float = 15.0
    verbose: bool = False

    @classmethod
    def from_env_and_args(cls, args: Optional[Any] = None, is_mock: Optional[bool] = None) -> "E2EConfig":
        mock_mode = bool(getattr(args, "mock", False)) if is_mock is None else bool(is_mock)

        default_user = "testuser" if mock_mode else "e2ecesar1114"
        default_pass = "CorrectP@ss123" if mock_mode else "Venta2026#"
        default_sec_user = "secondary_user" if mock_mode else "e2efinal20261114002928"
        default_sec_pass = "SecondaryP@ss123" if mock_mode else "Venta2026#"

        base_url = (
            getattr(args, "base_url", None)
            or os.environ.get("E2E_BASE_URL")
            or os.environ.get("BASE_URL")
            or "http://localhost:10000"
        )
        username = (
            getattr(args, "username", None)
            or os.environ.get("E2E_USERNAME")
            or os.environ.get("E2E_USER_A")
            or default_user
        )
        password = (
            getattr(args, "password", None)
            or os.environ.get("E2E_PASSWORD")
            or os.environ.get("E2E_PASS_A")
            or default_pass
        )
        sec_user = (
            getattr(args, "secondary_username", None)
            or os.environ.get("E2E_SECONDARY_USERNAME")
            or os.environ.get("E2E_USER_B")
            or default_sec_user
        )
        sec_pass = (
            getattr(args, "secondary_password", None)
            or os.environ.get("E2E_SECONDARY_PASSWORD")
            or os.environ.get("E2E_PASS_B")
            or default_sec_pass
        )
        otp = getattr(args, "otp", None) or os.environ.get("E2E_OTP") or os.environ.get("E2E_OTP_VALUE")
        imap_host = os.environ.get("E2E_IMAP_HOST")
        imap_port = int(os.environ.get("E2E_IMAP_PORT", "993"))
        imap_user = os.environ.get("E2E_IMAP_USER")
        imap_pass = os.environ.get("E2E_IMAP_PASS")
        live_safe = bool(getattr(args, "live_safe", False)) or (
            os.environ.get("E2E_LIVE_SAFE", "").lower() in ("1", "true", "yes")
        )
        allow_live_payments = bool(getattr(args, "allow_live_payments", False)) or (
            os.environ.get("E2E_ALLOW_LIVE_PAYMENTS", "").lower() in ("1", "true", "yes")
        )
        verbose = bool(getattr(args, "verbose", False))

        return cls(
            base_url=base_url,
            is_mock=mock_mode,
            username=username,
            password=password,
            secondary_username=sec_user,
            secondary_password=sec_pass,
            otp=otp,
            imap_host=imap_host,
            imap_port=imap_port,
            imap_user=imap_user,
            imap_pass=imap_pass,
            live_safe=live_safe,
            allow_live_payments=allow_live_payments,
            verbose=verbose,
        )


def assert_not_protected_account(username: str) -> None:
    """Guardrail preventing destructive tests (lockout, password/profile edit, biometric revoke) on shared accounts."""
    normalized = (username or "").strip()
    if normalized in PROTECTED_ACCOUNTS:
        raise ValueError(
            f"SAFETY VIOLATION: Refusing destructive/mutating test against protected shared account '{normalized}'. "
            "Use register_ephemeral_user() to create an isolated throwaway account instead."
        )


def generate_valid_ift_phone() -> str:
    """Generates a valid 10-digit Mexican IFT mobile number starting with 55 (non-repeating, non-sequential)."""
    while True:
        suffix = "".join(random.choices("0123456789", k=8))
        phone = f"55{suffix}"
        if len(set(phone)) > 1 and phone not in INVALID_PHONE_SEQUENCES:
            return phone


def build_ephemeral_user_payload(
    *,
    prefix: str = "e2etmp",
    password: str = "E2eTempPass#2026",
    fecha_nacimiento: str = "1995-05-15",
    email_domain: str = "bancodigital-e2e.mx",
    email_override: Optional[str] = None,
    segundo_nombre: str = "Antonio",
) -> Dict[str, Any]:
    """Builds a valid RegisterRequestDTO payload compliant with all backend & PROJECT.md rules."""
    ts_ms = int(time.time() * 1000) % 10_000_000
    rand_part = "".join(random.choices(string.ascii_lowercase + string.digits, k=5))
    usuario = f"{prefix}_{ts_ms}_{rand_part}"
    email_addr = email_override or f"{usuario}@{email_domain}"
    return {
        "nombre": "Carlos",
        "segundoNombre": segundo_nombre,
        "apellidoPaterno": "Mendoza",
        "apellidoMaterno": "Ruiz",
        "fechaNacimiento": fecha_nacimiento,
        "sexo": "HOMBRE",
        "correo": email_addr,
        "telefonoMovil": generate_valid_ift_phone(),
        "usuario": usuario,
        "contrasena": password,
    }


def register_ephemeral_user(
    client: Any,
    config: Optional[E2EConfig] = None,
    *,
    prefix: str = "e2etmp",
    password: str = "E2eTempPass#2026",
    fecha_nacimiento: str = "1995-05-15",
    email_override: Optional[str] = None,
) -> Dict[str, Any]:
    """Registers an isolated ephemeral user via POST /api/v1/auth/register and returns account metadata."""
    domain = config.test_email_domain if config else "bancodigital-e2e.mx"
    payload = build_ephemeral_user_payload(
        prefix=prefix,
        password=password,
        fecha_nacimiento=fecha_nacimiento,
        email_domain=domain,
        email_override=email_override,
    )
    assert_not_protected_account(payload["usuario"])
    resp = client.post("/api/v1/auth/register", payload)
    if resp.status_code != 201 or not isinstance(resp.json, dict):
        raise AssertionError(
            f"Failed to register ephemeral user {payload['usuario']}: "
            f"HTTP {resp.status_code} -> {resp.text[:300]}"
        )
    data = resp.json
    return {
        "usuario": payload["usuario"],
        "contrasena": password,
        "email": payload["correo"],
        "telefonoMovil": payload["telefonoMovil"],
        "token": data.get("token"),
        "numeroCuenta": data.get("numeroCuenta"),
        "saldo": float(data.get("saldo", 0.0)),
        "clienteId": data.get("clienteId"),
        "payload": payload,
        "response": data,
    }


def resolve_otp(
    client: Any,
    numero_cuenta: str,
    config: E2EConfig,
    generated_resp: Optional[Dict[str, Any]] = None,
) -> Optional[str]:
    """
    Resolves a 6-digit 2FA OTP without violating F9 non-exposure:
    - In mock mode: retrieves from out-of-band mock inbox GET /__mock/inbox/{numeroCuenta}/latest-otp.
    - In live mode: uses --otp / E2E_OTP, or IMAP inbox if configured.
    - Returns None if no OTP source is available (e.g. --live-safe without IMAP).
    """
    if config.is_mock:
        inbox_resp = client.get(f"/__mock/inbox/{numero_cuenta}/latest-otp")
        if inbox_resp.status_code == 200 and isinstance(inbox_resp.json, dict):
            code = inbox_resp.json.get("codigo2FA")
            if code and re.match(r"^\d{6}$", str(code)):
                return str(code)
        return None

    if config.otp and re.match(r"^\d{6}$", config.otp.strip()):
        return config.otp.strip()

    if config.imap_host and config.imap_user and config.imap_pass:
        return _poll_imap_for_otp(
            config.imap_host, config.imap_port, config.imap_user, config.imap_pass
        )

    if generated_resp and generated_resp.get("codigo2FA"):
        # Only if server explicitly ran with expose-otp-in-response=true in a dev test harness
        return str(generated_resp["codigo2FA"])

    return None


def _poll_imap_for_otp(
    host: str, port: int, user: str, password: str, max_wait_s: float = 15.0
) -> Optional[str]:
    """Polls an IMAP mailbox for the latest 6-digit GestoPago/Banco Digital 2FA OTP."""
    deadline = time.time() + max_wait_s
    while time.time() < deadline:
        try:
            with imaplib.IMAP4_SSL(host, port) as mail:
                mail.login(user, password)
                mail.select("INBOX")
                status, data = mail.search(None, "ALL")
                if status == "OK" and data and data[0]:
                    ids = data[0].split()[-5:]
                    for msg_id in reversed(ids):
                        _, msg_data = mail.fetch(msg_id, "(RFC822)")
                        raw = msg_data[0][1]
                        msg = email_pkg.message_from_bytes(raw)
                        subject = str(msg.get("Subject", ""))
                        body_text = ""
                        if msg.is_multipart():
                            for part in msg.walk():
                                if part.get_content_type() in ("text/plain", "text/html"):
                                    payload = part.get_payload(decode=True)
                                    if payload:
                                        body_text += payload.decode("utf-8", errors="ignore")
                        else:
                            payload = msg.get_payload(decode=True)
                            if payload:
                                body_text = payload.decode("utf-8", errors="ignore")
                        if "2fa" in subject.lower() or "código" in subject.lower() or "codigo" in body_text.lower():
                            match = re.search(r"\b(\d{6})\b", body_text)
                            if match:
                                return match.group(1)
        except Exception:
            pass
        time.sleep(2.0)
    return None


# -----------------------------------------------------------------------------
# Dynamic Catalog & Mathematical Check-Digit Helpers
# -----------------------------------------------------------------------------

def extract_products(catalog_json: Any) -> List[Dict[str, Any]]:
    """Extracts ProductDTO list from /productos response supporting `data` (official) or `productos`."""
    if isinstance(catalog_json, list):
        return catalog_json
    if isinstance(catalog_json, dict):
        if isinstance(catalog_json.get("data"), list):
            return catalog_json["data"]
        if isinstance(catalog_json.get("productos"), list):
            return catalog_json["productos"]
    return []


def product_price(prod: Dict[str, Any]) -> float:
    raw = prod.get("price", prod.get("precio", 0))
    try:
        return float(raw)
    except (TypeError, ValueError):
        return 0.0


def product_front_type(prod: Dict[str, Any]) -> str:
    return str(prod.get("frontType", prod.get("tipoFront", ""))).strip()


def product_ref_type(prod: Dict[str, Any]) -> str:
    return str(prod.get("referenceType", prod.get("tipoReferencia", ""))).strip().lower()


def product_service_id(prod: Dict[str, Any]) -> int:
    return int(prod.get("serviceId", prod.get("idServicio", 0)))


def product_id(prod: Dict[str, Any]) -> int:
    return int(prod.get("id", prod.get("idProducto", 0)))


def product_service_name(prod: Dict[str, Any]) -> str:
    return str(prod.get("service", prod.get("servicio", ""))).strip()


def product_name(prod: Dict[str, Any]) -> str:
    return str(prod.get("name", prod.get("producto", ""))).strip()


def product_has_dv(prod: Dict[str, Any]) -> bool:
    val = prod.get("hasVerificationDigit", prod.get("hasDigitoVerificador", False))
    if isinstance(val, bool):
        return val
    return str(val).strip().lower() == "true"


def find_telcel_package(products: List[Dict[str, Any]], target_price: float = 20.0) -> Optional[Dict[str, Any]]:
    """Dynamically locates a Telcel fixed-price airtime package (never hardcoding stale ID 166)."""
    matches = [
        p for p in products
        if "telcel" in product_service_name(p).lower()
        and product_front_type(p) == "1"
        and abs(product_price(p) - target_price) < 0.01
    ]
    if matches:
        return matches[0]
    fallback = [
        p for p in products
        if product_front_type(p) == "1" and product_price(p) > 0 and product_price(p) <= 50.0
    ]
    return fallback[0] if fallback else None


def find_cfe_service(products: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    matches = [
        p for p in products
        if "cfe" in product_service_name(p).lower() and product_front_type(p) == "2"
    ]
    if matches:
        return matches[0]
    fallback = [p for p in products if product_front_type(p) == "2"]
    return fallback[0] if fallback else None


def find_telmex_service(products: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    matches = [
        p for p in products
        if "telmex" in product_service_name(p).lower()
        and product_front_type(p) == "2"
        and product_ref_type(p) == "c"
    ]
    if matches:
        return matches[0]
    return find_by_front_and_ref(products, "2", "c")


def find_by_front_and_ref(
    products: List[Dict[str, Any]], front: str, ref_type: str
) -> Optional[Dict[str, Any]]:
    for p in products:
        if product_front_type(p) == str(front) and product_ref_type(p) == ref_type.lower():
            return p
    return None


def find_blocked_front45(products: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    for p in products:
        if product_front_type(p) in ("4", "5"):
            return p
    return None


def find_luhn_product(products: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    for p in products:
        if product_has_dv(p):
            return p
    return None


def compute_luhn_check_digit(payload_digits: str) -> str:
    """Computes standard Luhn Mod-10 check digit so payload_digits + check_digit passes Luhn."""
    digits = [int(d) for d in payload_digits]
    total = 0
    double = True
    for d in reversed(digits):
        val = d * 2 if double else d
        if val > 9:
            val -= 9
        total += val
        double = not double
    return str((10 - (total % 10)) % 10)


def generate_luhn_mod10_ref(base_digits: str = "411111111") -> Tuple[str, str]:
    """Returns (valid_ref, corrupted_ref) for Luhn Mod-10 verification."""
    check = compute_luhn_check_digit(base_digits)
    valid = f"{base_digits}{check}"
    bad_digit = str((int(check) + 1) % 10)
    corrupted = f"{base_digits}{bad_digit}"
    return valid, corrupted
