"""
Tier 1: Feature Coverage E2E Test Suite (35 API Tests across E1-E7).
Covers primary happy-path functionality and contracts defined in PROJECT.md § Interface Contracts.
"""

from typing import Any, Dict, List, Optional
from config import (
    E2EConfig,
    extract_products,
    find_by_front_and_ref,
    find_cfe_service,
    find_telcel_package,
    find_telmex_service,
    generate_valid_ift_phone,
    product_id,
    product_price,
    product_service_id,
    product_service_name,
    register_ephemeral_user,
    resolve_otp,
)
from test_client import (
    E2EClient,
    SkipTest,
    assert_any_field_present,
    assert_field_equals,
    assert_field_present,
    assert_is_none_or_absent,
    assert_pdf_bytes,
    assert_regex_match,
    assert_status,
)


def features(*tags: str):
    def decorator(fn):
        fn._features = tuple(t.upper() for t in tags)
        return fn
    return decorator


class Tier1FeatureCoverage:
    """Tier 1 Feature Coverage Test Group (E1-E7, 35 tests)."""

    def __init__(self, client: E2EClient, config: Optional[E2EConfig] = None):
        self.client = client
        self.config = config or E2EConfig()

    def _login_primary(self) -> Dict[str, Any]:
        resp = self.client.post(
            "/api/v1/auth/login",
            {"usuario": self.config.username, "contrasena": self.config.password},
        )
        assert_status(resp, 200, "Primary user login must succeed")
        return resp.json

    def _get_catalog(self) -> List[Dict[str, Any]]:
        resp = self.client.get("/api/v1/gestopago/productos")
        assert_status(resp, 200, "Catalog fetch must succeed")
        products = extract_products(resp.json)
        assert len(products) > 0, "Catalog must be non-empty"
        return products

    def _ensure_two_transactions_for_history(self, c: E2EClient, cuenta: str) -> List[Dict[str, Any]]:
        hist = c.post("/api/v1/pagos/historial", {"numeroCuenta": cuenta})
        assert_status(hist, 200, "History query must succeed")
        pagos = hist.json.get("pagos", [])
        if len(pagos) >= 2:
            return pagos
        # If running against a fresh account and mock/OTP is available, execute payments
        products = self._get_catalog()
        telcel = find_telcel_package(products, 20.0)
        if telcel and (self.config.is_mock or (self.config.allow_live_payments and self.config.otp)):
            while len(pagos) < 2:
                gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": cuenta})
                assert_status(gen, 200)
                otp = resolve_otp(c, cuenta, self.config, gen.json)
                if not otp:
                    break
                phone = generate_valid_ift_phone()
                pay = c.post(
                    "/api/v1/pagos/servicios",
                    {
                        "numeroCuenta": cuenta,
                        "idServicio": product_service_id(telcel),
                        "idProducto": product_id(telcel),
                        "monto": product_price(telcel),
                        "telefono": phone,
                        "referencia": phone,
                        "codigo2FA": otp,
                    },
                )
                assert_status(pay, 200)
                hist = c.post("/api/v1/pagos/historial", {"numeroCuenta": cuenta})
                pagos = hist.json.get("pagos", [])
        return pagos

    # =========================================================================
    # E1: Authentication, Session Protection & Mobile Biometrics (5 tests)
    # =========================================================================

    @features("E1")
    def test_t1_e1_01_login_success(self):
        """T1_E1_01: Successful login returns 200, LoginResponseDTO with token, account, balance, phone."""
        data = self._login_primary()
        assert_field_present(data, "token")
        assert_field_equals(data, "usuario", self.config.username)
        assert_field_present(data, "email")
        assert_regex_match(data["numeroCuenta"], r"^\d{16}$", "Account must be 16 digits")
        assert_regex_match(data.get("telefonoMovil", ""), r"^\d{10}$", "Phone must be 10 digits")
        assert float(data["saldo"]) >= 0.0, "Balance must be non-negative"
        assert len(str(data["token"])) > 10, "Token must be non-empty"

    @features("E1")
    def test_t1_e1_02_auth_me_profile(self):
        """T1_E1_02: GET /api/v1/auth/me with Bearer token returns full UserProfileDTO."""
        auth = self._login_primary()
        c = self.client.with_token(auth["token"])
        resp = c.get("/api/v1/auth/me")
        assert_status(resp, 200, "GET /auth/me must return 200")
        data = resp.json
        for field in ("clienteId", "usuario", "nombreCompleto", "email", "telefonoMovil", "numeroCuenta", "saldo"):
            assert_field_present(data, field)
        assert_field_equals(data, "usuario", self.config.username)
        if "activo" in data:
            assert data["activo"] is True, "Account must be active"

    @features("E1")
    def test_t1_e1_03_logout_invalidates_session(self):
        """T1_E1_03: POST /api/v1/auth/logout invalidates the Bearer token (subsequent /me -> 401)."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2et1e1")
        c = self.client.with_token(eph["token"])
        logout_resp = c.post("/api/v1/auth/logout", {"usuario": eph["usuario"]})
        assert_status(logout_resp, 200, "Logout must return 200")
        assert_field_present(logout_resp.json, "mensaje")

        me_after = c.get("/api/v1/auth/me")
        assert_status(me_after, 401, "Logged-out token must be rejected with 401")

    @features("E1")
    def test_t1_e1_04_biometric_enroll_and_status(self):
        """T1_E1_04: Enroll 64-char biometric token and verify GET /biometria/estado returns habilitada=true."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2ebio")
        c = self.client.with_token(eph["token"])
        bio_token = "b" * 64
        reg_resp = c.post(
            "/api/v1/auth/biometria/registrar",
            {"tokenBiometrico": bio_token, "dispositivo": "Pixel 8 Pro"},
        )
        assert_status(reg_resp, 200, "Biometric registration must succeed")
        assert_field_equals(reg_resp.json, "habilitada", True)
        assert_field_equals(reg_resp.json, "tipo", "HUELLA")
        assert_field_equals(reg_resp.json, "usuario", eph["usuario"])

        status_resp = c.get("/api/v1/auth/biometria/estado")
        assert_status(status_resp, 200, "Biometric status must succeed")
        assert_field_equals(status_resp.json, "habilitada", True)

    @features("E1")
    def test_t1_e1_05_biometric_login(self):
        """T1_E1_05: POST /api/v1/auth/login/biometrico authenticates enrolled ephemeral user."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2ebiol")
        c = self.client.with_token(eph["token"])
        bio_token = "c" * 64
        assert_status(
            c.post("/api/v1/auth/biometria/registrar", {"tokenBiometrico": bio_token, "dispositivo": "iPhone 15"}),
            200,
        )
        bio_login = self.client.post(
            "/api/v1/auth/login/biometrico",
            {"usuario": eph["usuario"], "tokenBiometrico": bio_token},
        )
        assert_status(bio_login, 200, "Biometric login must return 200")
        assert_field_present(bio_login.json, "token")
        assert_field_equals(bio_login.json, "numeroCuenta", eph["numeroCuenta"])
        assert_field_present(bio_login.json, "saldo")

    # =========================================================================
    # E2: Dynamic GestoPago Catalog & Provider Logos CDN (5 tests)
    # =========================================================================

    @features("E2")
    def test_t1_e2_01_flat_product_list(self):
        """T1_E2_01: GET /api/v1/gestopago/productos returns 200 with status OK and non-empty data[]."""
        resp = self.client.get("/api/v1/gestopago/productos")
        assert_status(resp, 200)
        data = resp.json
        assert isinstance(data, dict), "Catalog response must be JSON object"
        assert_field_equals(data, "status", "OK")
        products = extract_products(data)
        assert len(products) > 0, "Catalog data[] must not be empty"

    @features("E2")
    def test_t1_e2_02_grouped_products(self):
        """T1_E2_02: GET /api/v1/gestopago/productos/agrupados returns grouped products by tipoFront."""
        resp = self.client.get("/api/v1/gestopago/productos/agrupados")
        assert_status(resp, 200)
        data = resp.json
        assert_field_present(data, "totalProductos")
        assert_field_present(data, "totalGrupos")
        assert int(data["totalProductos"]) > 0
        assert int(data["totalGrupos"]) >= 2
        grupos = data.get("grupos", [])
        assert len(grupos) >= 2
        for grp in grupos:
            assert_field_present(grp, "tipoFront")
            assert isinstance(grp.get("productos"), list) and len(grp["productos"]) > 0

    @features("E2")
    def test_t1_e2_03_product_dto_schema(self):
        """T1_E2_03: ProductDTO includes id, name, service, serviceId, price, referenceType, frontType, categoryId, logoUrl, showAyuda."""
        products = self._get_catalog()
        first = products[0]
        for field in (
            "id",
            "name",
            "service",
            "serviceId",
            "price",
            "referenceType",
            "frontType",
            "categoryId",
            "logoUrl",
            "showAyuda",
        ):
            assert_field_present(first, field, f"ProductDTO missing required field {field}")
        assert "legend" in first, "ProductDTO must include legend field (nullable)"
        assert isinstance(first["showAyuda"], bool), "showAyuda must be boolean"

    @features("E2")
    def test_t1_e2_04_live_major_providers_no_static_xml(self):
        """T1_E2_04: Catalog contains major active providers (Telcel, CFE, Telmex) without static XML fallback."""
        resp = self.client.get("/api/v1/gestopago/productos")
        assert_status(resp, 200)
        origen = str(resp.json.get("origenCache", "")).upper()
        assert "XML" not in origen and "STATIC" not in origen, f"Static XML fallback detected: {origen}"
        products = extract_products(resp.json)
        services_upper = {product_service_name(p).upper() for p in products}
        for expected in ("TELCEL", "CFE", "TELMEX"):
            assert any(expected in s for s in services_upper), f"Provider {expected} missing from catalog"

    @features("E2")
    def test_t1_e2_05_provider_logo_cdn_urls(self):
        """T1_E2_05: Every ProductDTO logoUrl matches official gestopago.portalventas.net PNG CDN format."""
        products = self._get_catalog()
        pattern = r"^https://gestopago\.portalventas\.net/sistema/images/gestopago/servicios/\d+\.png$"
        for prod in products[:25]:
            assert_regex_match(prod.get("logoUrl", ""), pattern, f"Invalid logoUrl on product {prod.get('id')}")

    # =========================================================================
    # E3: Reference Validation & Payment Gating (5 tests)
    # =========================================================================

    @features("E3")
    def test_t1_e3_01_validate_telcel_front1_phone(self):
        """T1_E3_01: Valid 10-digit IFT phone passes validar-referencia for Telcel tipoFront=1."""
        products = self._get_catalog()
        telcel = find_telcel_package(products, 20.0)
        assert telcel is not None, "Telcel package not found in catalog"
        resp = self.client.post(
            "/api/v1/pagos/validar-referencia",
            {
                "idServicio": product_service_id(telcel),
                "idProducto": product_id(telcel),
                "referencia": "5512345678",
                "monto": product_price(telcel),
            },
        )
        assert_status(resp, 200)
        assert_field_equals(resp.json, "esValida", True)
        assert_field_equals(resp.json, "tipoFront", 1)
        assert_field_equals(resp.json, "requiereMonto", False)

    @features("E3")
    def test_t1_e3_02_validate_front2_type_b_barcode(self):
        """T1_E3_02: Valid 30-digit barcode passes validar-referencia for tipoFront=2, tipoReferencia='b'."""
        products = self._get_catalog()
        svc = find_by_front_and_ref(products, "2", "b")
        if not svc:
            raise SkipTest("No tipoFront=2, tipoReferencia=b product in live catalog")
        resp = self.client.post(
            "/api/v1/pagos/validar-referencia",
            {
                "idServicio": product_service_id(svc),
                "idProducto": product_id(svc),
                "referencia": "010101010101010101010101010101",
                "monto": 50.0,
            },
        )
        assert_status(resp, 200)
        assert_field_equals(resp.json, "esValida", True)
        assert_field_equals(resp.json, "tipoFront", 2)
        assert_field_equals(resp.json, "tipoReferencia", "b")

    @features("E3")
    def test_t1_e3_03_validate_front2_type_c_contract(self):
        """T1_E3_03: Valid 10-digit numeric contract passes validar-referencia for tipoReferencia='c'."""
        products = self._get_catalog()
        svc = find_telmex_service(products) or find_by_front_and_ref(products, "2", "c")
        if not svc:
            raise SkipTest("No tipoFront=2, tipoReferencia=c product in catalog")
        resp = self.client.post(
            "/api/v1/pagos/validar-referencia",
            {
                "idServicio": product_service_id(svc),
                "idProducto": product_id(svc),
                "referencia": "5512345678",
                "monto": 100.0,
            },
        )
        assert_status(resp, 200)
        assert_field_equals(resp.json, "esValida", True)
        assert_field_equals(resp.json, "tipoReferencia", "c")

    @features("E3")
    def test_t1_e3_04_validate_front2_type_a_alphanumeric(self):
        """T1_E3_04: Valid alphanumeric reference passes validar-referencia for tipoReferencia='a'."""
        products = self._get_catalog()
        svc = find_by_front_and_ref(products, "2", "a")
        if not svc:
            raise SkipTest("No tipoFront=2, tipoReferencia=a product in catalog")
        resp = self.client.post(
            "/api/v1/pagos/validar-referencia",
            {
                "idServicio": product_service_id(svc),
                "idProducto": product_id(svc),
                "referencia": "REF2026ABC",
                "monto": 50.0,
            },
        )
        assert_status(resp, 200)
        assert_field_equals(resp.json, "esValida", True)
        assert_field_equals(resp.json, "tipoReferencia", "a")

    @features("E3")
    def test_t1_e3_05_validate_front2_type_bc_dual(self):
        """T1_E3_05: tipoReferencia='bc' accepts both 11-digit contract and 24-digit barcode."""
        products = self._get_catalog()
        svc = find_cfe_service(products) or find_by_front_and_ref(products, "2", "bc")
        if not svc:
            raise SkipTest("No tipoFront=2, tipoReferencia=bc product in catalog")
        for ref in ("12345678901", "010101010101010101010101"):
            resp = self.client.post(
                "/api/v1/pagos/validar-referencia",
                {
                    "idServicio": product_service_id(svc),
                    "idProducto": product_id(svc),
                    "referencia": ref,
                    "monto": 50.0,
                },
            )
            assert_status(resp, 200)
            assert_field_equals(resp.json, "esValida", True, f"bc ref {ref} must be valid")

    # =========================================================================
    # E4: 2FA OTP Security, Payment Confirmation & 3-State Results (5 tests)
    # =========================================================================

    @features("E4")
    def test_t1_e4_01_generate_2fa_no_otp_leak(self):
        """T1_E4_01: POST /api/v1/pagos/2fa/generar sends Brevo email without exposing codigo2FA."""
        auth = self._login_primary()
        c = self.client.with_token(auth["token"])
        resp = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": auth["numeroCuenta"]})
        assert_status(resp, 200)
        data = resp.json
        assert_is_none_or_absent(data, "codigo2FA", "F9 forbids returning codigo2FA in JSON response")
        assert_field_equals(data, "correoEnviado", True)
        ttl = assert_any_field_present(data, ("vigenciaSegundos", "expiraEnSegundos"), "2FA TTL required")
        assert int(ttl) == 300, f"Expected 300s TTL, got {ttl}"
        assert_regex_match(data.get("correoDestino", ""), r"\*\*\*@", "correoDestino must be masked")
        msg_id = assert_any_field_present(data, ("brevoMessageId", "messageId"), "Brevo message ID required")
        assert len(str(msg_id)) > 0

    @features("E4")
    def test_t1_e4_02_payment_telcel_exitosa(self):
        """T1_E4_02: Telcel $20 payment completes with estadoOperacion=EXITOSA and exact balance deduction."""
        if not self.config.is_mock and (self.config.live_safe or not self.config.allow_live_payments):
            raise SkipTest("Skipped live payment mutation under --live-safe / default safe mode")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2epay1")
        c = self.client.with_token(eph["token"])
        telcel = find_telcel_package(self._get_catalog(), 20.0)
        assert telcel is not None

        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        assert_status(gen, 200)
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json)
        if not otp:
            raise SkipTest("No OTP available (configure E2E_OTP or IMAP for live payment)")

        phone = generate_valid_ift_phone()
        pay = c.post(
            "/api/v1/pagos/servicios",
            {
                "numeroCuenta": eph["numeroCuenta"],
                "idServicio": product_service_id(telcel),
                "idProducto": product_id(telcel),
                "monto": product_price(telcel),
                "telefono": phone,
                "referencia": phone,
                "codigo2FA": otp,
            },
        )
        assert_status(pay, 200)
        pdata = pay.json
        assert_field_equals(pdata, "estadoOperacion", "EXITOSA")
        assert_regex_match(pdata.get("folioTicket", ""), r"^FT-\d{8,14}-[A-Z0-9]+$")
        assert_field_present(pdata, "numeroAutorizacion")
        assert_field_equals(pdata, "correoEnviado", True)
        expected_bal = round(eph["saldo"] - product_price(telcel), 2)
        assert abs(float(pdata["saldoRestanteCuenta"]) - expected_bal) < 0.01

    @features("E4")
    def test_t1_e4_03_payment_cfe_open_amount_exitosa(self):
        """T1_E4_03: CFE tipoFront=2 open-amount $20.00 payment succeeds with EXITOSA and logoUrl."""
        if not self.config.is_mock and (self.config.live_safe or not self.config.allow_live_payments):
            raise SkipTest("Skipped live payment mutation under --live-safe / default safe mode")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2ecfe")
        c = self.client.with_token(eph["token"])
        cfe = find_cfe_service(self._get_catalog())
        assert cfe is not None
        sid = product_service_id(cfe)

        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        assert_status(gen, 200)
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json)
        if not otp:
            raise SkipTest("No OTP available")

        pay = c.post(
            "/api/v1/pagos/servicios",
            {
                "numeroCuenta": eph["numeroCuenta"],
                "idServicio": sid,
                "idProducto": product_id(cfe),
                "monto": 20.00,
                "referencia": "010101010101010101010101010101",
                "codigo2FA": otp,
            },
        )
        assert_status(pay, 200)
        pdata = pay.json
        assert_field_equals(pdata, "estadoOperacion", "EXITOSA")
        assert abs(float(pdata["montoPagado"]) - 20.00) < 0.01
        assert float(pdata.get("comision", 0.0)) >= 0.0
        assert f"/{sid}.png" in str(pdata.get("logoUrl", ""))

    @features("E4")
    def test_t1_e4_04_payment_provider_rechazada(self):
        """T1_E4_04: Provider rejection returns 400/409/422 with estadoOperacion=RECHAZADA and untouched balance."""
        if not self.config.is_mock:
            raise SkipTest("Provider fault-injection RECHAZADA test requires --mock mode")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2erej")
        c = self.client.with_token(eph["token"])
        cfe = find_cfe_service(self._get_catalog())
        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json)

        resp = c.post(
            "/api/v1/pagos/servicios",
            {
                "numeroCuenta": eph["numeroCuenta"],
                "idServicio": product_service_id(cfe),
                "idProducto": product_id(cfe),
                "monto": 25.00,
                "referencia": "RECHAZAR999999",
                "codigo2FA": otp,
            },
        )
        assert_status(resp, (400, 409, 422))
        assert_field_equals(resp.json, "estadoOperacion", "RECHAZADA")
        me_after = c.get("/api/v1/auth/me")
        assert abs(float(me_after.json["saldo"]) - eph["saldo"]) < 0.01

    @features("E4")
    def test_t1_e4_05_payment_timeout_pendiente(self):
        """T1_E4_05: Provider timeout returns 504/202 with estadoOperacion=PENDIENTE, upc, and untouched balance."""
        if not self.config.is_mock:
            raise SkipTest("Provider timeout fault-injection PENDIENTE test requires --mock mode")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2etmo")
        c = self.client.with_token(eph["token"])
        cfe = find_cfe_service(self._get_catalog())
        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json)

        resp = c.post(
            "/api/v1/pagos/servicios",
            {
                "numeroCuenta": eph["numeroCuenta"],
                "idServicio": product_service_id(cfe),
                "idProducto": product_id(cfe),
                "monto": 25.00,
                "referencia": "TIMEOUT999999",
                "codigo2FA": otp,
            },
        )
        assert_status(resp, (504, 202))
        assert_field_equals(resp.json, "estadoOperacion", "PENDIENTE")
        assert_field_present(resp.json, "upc")
        me_after = c.get("/api/v1/auth/me")
        assert abs(float(me_after.json["saldo"]) - eph["saldo"]) < 0.01

    # =========================================================================
    # E5: User-Scoped Payment History & PDF/HTML Receipts (5 tests)
    # =========================================================================

    @features("E5")
    def test_t1_e5_01_history_schema_count_parity(self):
        """T1_E5_01: POST /api/v1/pagos/historial returns totalMovimientos == len(pagos)."""
        auth = self._login_primary()
        c = self.client.with_token(auth["token"])
        resp = c.post("/api/v1/pagos/historial", {"numeroCuenta": auth["numeroCuenta"]})
        assert_status(resp, 200)
        data = resp.json
        assert_field_equals(data, "numeroCuenta", auth["numeroCuenta"])
        assert_field_present(data, "totalMovimientos")
        assert isinstance(data.get("pagos"), list)
        assert int(data["totalMovimientos"]) == len(data["pagos"])

    @features("E5")
    def test_t1_e5_02_history_item_fields(self):
        """T1_E5_02: History items contain folioTicket, numeroAutorizacion, servicio, producto, monto, estado, logoUrl."""
        auth = self._login_primary()
        c = self.client.with_token(auth["token"])
        pagos = self._ensure_two_transactions_for_history(c, auth["numeroCuenta"])
        if not pagos:
            raise SkipTest("No transactions available in live account history")
        item = pagos[0]
        for field in (
            "folioTicket",
            "numeroAutorizacion",
            "servicio",
            "producto",
            "monto",
            "comision",
            "fechaOperacion",
            "estado",
            "logoUrl",
        ):
            assert_field_present(item, field)

    @features("E5")
    def test_t1_e5_03_history_descending_order(self):
        """T1_E5_03: Payment history items are ordered descending by fechaOperacion."""
        auth = self._login_primary()
        c = self.client.with_token(auth["token"])
        pagos = self._ensure_two_transactions_for_history(c, auth["numeroCuenta"])
        if len(pagos) < 2:
            raise SkipTest("Need >= 2 transactions in history to verify descending sort")
        for idx in range(len(pagos) - 1):
            curr_ts = str(pagos[idx]["fechaOperacion"])
            next_ts = str(pagos[idx + 1]["fechaOperacion"])
            assert curr_ts >= next_ts, f"History not descending at index {idx}: {curr_ts} < {next_ts}"

    @features("E5")
    def test_t1_e5_04_receipt_resend_brevo(self):
        """T1_E5_04: POST /api/v1/pagos/comprobante/reenviar dispatches HTML+PDF receipt email."""
        auth = self._login_primary()
        c = self.client.with_token(auth["token"])
        pagos = self._ensure_two_transactions_for_history(c, auth["numeroCuenta"])
        if not pagos:
            raise SkipTest("No folio available for receipt resend")
        folio = pagos[0]["folioTicket"]
        resp = c.post(
            "/api/v1/pagos/comprobante/reenviar",
            {"numeroCuenta": auth["numeroCuenta"], "folioTicket": folio},
        )
        assert_status(resp, 200)
        assert_field_equals(resp.json, "enviado", True)
        assert_field_present(resp.json, "correoDestino")
        assert_any_field_present(resp.json, ("messageId", "brevoMessageId"))

    @features("E5")
    def test_t1_e5_05_receipt_pdf_download(self):
        """T1_E5_05: GET /api/v1/pagos/comprobante/{folio}/pdf returns valid application/pdf %PDF- bytes."""
        auth = self._login_primary()
        c = self.client.with_token(auth["token"])
        pagos = self._ensure_two_transactions_for_history(c, auth["numeroCuenta"])
        if not pagos:
            raise SkipTest("No folio available for PDF receipt download")
        folio = pagos[0]["folioTicket"]
        resp = c.get(f"/api/v1/pagos/comprobante/{folio}/pdf", headers={"Accept": "application/pdf"})
        assert_status(resp, 200)
        assert_pdf_bytes(resp, min_bytes=400, message="PDF receipt download")

    # =========================================================================
    # E6: Account Lifecycle — Register, Profile, Password, Top-Ups (5 tests)
    # =========================================================================

    @features("E6")
    def test_t1_e6_01_register_new_account_welcome_balance(self):
        """T1_E6_01: POST /api/v1/auth/register creates 16-digit account with $200.00 welcome balance."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2ereg")
        assert_regex_match(eph["numeroCuenta"], r"^\d{16}$")
        assert abs(eph["saldo"] - 200.00) < 0.01, f"Expected $200.00 welcome balance, got {eph['saldo']}"

    @features("E6")
    def test_t1_e6_02_update_profile_me_perfil(self):
        """T1_E6_02: PUT /api/v1/auth/me/perfil updates split names, email, phone and persists in /me."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2eperf")
        c = self.client.with_token(eph["token"])
        new_phone = generate_valid_ift_phone()
        new_email = f"{eph['usuario']}.upd@{self.config.test_email_domain}"
        resp = c.put(
            "/api/v1/auth/me/perfil",
            {
                "nombre": "Andres",
                "segundoNombre": "Felipe",
                "apellidoPaterno": "Salazar",
                "apellidoMaterno": "Vega",
                "correo": new_email,
                "telefonoMovil": new_phone,
            },
        )
        assert_status(resp, 200)
        assert_field_equals(resp.json, "email", new_email)
        assert_field_equals(resp.json, "telefonoMovil", new_phone)
        assert "Andres" in resp.json.get("nombreCompleto", "")

        me_resp = c.get("/api/v1/auth/me")
        assert_status(me_resp, 200)
        assert_field_equals(me_resp.json, "email", new_email)
        assert_field_equals(me_resp.json, "telefonoMovil", new_phone)

    @features("E6")
    def test_t1_e6_03_update_contact_me_contacto(self):
        """T1_E6_03: PUT /api/v1/auth/me/contacto updates email and phone cleanly."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2econt")
        c = self.client.with_token(eph["token"])
        new_phone = generate_valid_ift_phone()
        new_email = f"{eph['usuario']}.cnt@{self.config.test_email_domain}"
        resp = c.put(
            "/api/v1/auth/me/contacto",
            {"correo": new_email, "telefonoMovil": new_phone},
        )
        assert_status(resp, 200)
        assert_field_equals(resp.json, "email", new_email)
        assert_field_equals(resp.json, "telefonoMovil", new_phone)

    @features("E6")
    def test_t1_e6_04_change_password_and_login_new(self):
        """T1_E6_04: PUT /api/v1/auth/me/contrasena updates password and allows login with new password."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2epwd")
        c = self.client.with_token(eph["token"])
        new_pass = "NewSecurePass#99"
        resp = c.put(
            "/api/v1/auth/me/contrasena",
            {"contrasenaActual": eph["contrasena"], "contrasenaNueva": new_pass},
        )
        assert_status(resp, 200)
        assert_field_present(resp.json, "mensaje")

        login_new = self.client.post(
            "/api/v1/auth/login",
            {"usuario": eph["usuario"], "contrasena": new_pass},
        )
        assert_status(login_new, 200, "New password must authenticate cleanly")

    @features("E6")
    def test_t1_e6_05_abonar_saldo(self):
        """T1_E6_05: POST /api/v1/auth/me/abonar credits $150.50 atomically to ephemeral $200.00 balance."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2eabn")
        c = self.client.with_token(eph["token"])
        resp = c.post(
            "/api/v1/auth/me/abonar",
            {"monto": 150.50, "concepto": "Abono E2E Tier 1"},
        )
        assert_status(resp, 200)
        assert abs(float(resp.json["saldo"]) - 350.50) < 0.01

    # =========================================================================
    # E7: Negative Security, Auth & Cross-Account IDOR Enforcement (5 tests)
    # =========================================================================

    @features("E7")
    def test_t1_e7_01_unauthenticated_me_returns_401(self):
        """T1_E7_01: Unauthenticated GET /api/v1/auth/me returns 401 Unauthorized."""
        anon = self.client.with_token(None)
        resp = anon.get("/api/v1/auth/me")
        assert_status(resp, 401)

    @features("E7")
    def test_t1_e7_02_unauthenticated_pagos_endpoints_return_401(self):
        """T1_E7_02: Unauthenticated /pagos/* endpoints all return 401 Unauthorized."""
        anon = self.client.with_token(None)
        calls = [
            anon.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": "1234567890123456"}),
            anon.post(
                "/api/v1/pagos/servicios",
                {
                    "numeroCuenta": "1234567890123456",
                    "idServicio": 133,
                    "idProducto": 403,
                    "monto": 20.0,
                    "referencia": "5512345678",
                    "codigo2FA": "123456",
                },
            ),
            anon.post("/api/v1/pagos/historial", {"numeroCuenta": "1234567890123456"}),
            anon.post(
                "/api/v1/pagos/comprobante/reenviar",
                {"numeroCuenta": "1234567890123456", "folioTicket": "FT-20261119-FAKE"},
            ),
            anon.get("/api/v1/pagos/comprobante/FT-20261119-FAKE/pdf"),
        ]
        for idx, r in enumerate(calls):
            assert_status(r, 401, f"Unauthenticated /pagos endpoint #{idx} must return 401")

    @features("E7")
    def test_t1_e7_03_unauthenticated_legacy_endpoints_return_401(self):
        """T1_E7_03: Unauthenticated legacy/admin endpoints (/clientes, /cuentas, /personas) return 401."""
        anon = self.client.with_token(None)
        assert_status(anon.get("/api/v1/clientes"), 401, "/api/v1/clientes must require auth")
        assert_status(anon.post("/api/v1/cuentas/saldo", {"numeroCuenta": "1234567890123456"}), 401)
        assert_status(anon.post("/personas", {"nombre": "Test"}), 401)

    @features("E7")
    def test_t1_e7_04_actuator_health_up(self):
        """T1_E7_04: GET /actuator/health returns 200 OK with status=UP."""
        resp = self.client.get("/actuator/health")
        assert_status(resp, 200, "Health endpoint must return 200 (never 503)")
        assert_field_equals(resp.json, "status", "UP")

    @features("E7")
    def test_t1_e7_05_cors_allows_web_origin_no_wildcard(self):
        """T1_E7_05: OPTIONS preflight for http://localhost:5173 returns explicit origin (never *)."""
        resp = self.client.options(
            "/api/v1/auth/login",
            headers={"Origin": "http://localhost:5173"},
        )
        assert_status(resp, (200, 204))
        acao = resp.header("Access-Control-Allow-Origin")
        assert acao == "http://localhost:5173", f"Expected explicit origin, got {acao!r}"
