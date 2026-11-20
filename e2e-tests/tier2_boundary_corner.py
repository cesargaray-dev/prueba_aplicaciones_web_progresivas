"""
Tier 2: Boundary & Corner Cases E2E Test Suite (42 API Tests across E1-E7, 6 per area).
Verifies BVA, input sanitation, security invariants, idempotency, and IDOR/auth enforcement.
"""

from datetime import date
from typing import Any, Dict, List, Optional
from config import (
    E2EConfig,
    assert_not_protected_account,
    build_ephemeral_user_payload,
    extract_products,
    find_blocked_front45,
    find_by_front_and_ref,
    find_cfe_service,
    find_luhn_product,
    find_telcel_package,
    generate_luhn_mod10_ref,
    generate_valid_ift_phone,
    product_front_type,
    product_id,
    product_name,
    product_price,
    product_service_id,
    register_ephemeral_user,
    resolve_otp,
)
from test_client import (
    E2EClient,
    SkipTest,
    assert_field_equals,
    assert_field_present,
    assert_status,
)
from tier1_feature_coverage import features


class Tier2BoundaryCorner:
    """Tier 2 Boundary & Corner Cases Test Group (E1-E7, 42 tests)."""

    def __init__(self, client: E2EClient, config: Optional[E2EConfig] = None):
        self.client = client
        self.config = config or E2EConfig()

    def _login(self, usuario: str, contrasena: str) -> Dict[str, Any]:
        resp = self.client.post("/api/v1/auth/login", {"usuario": usuario, "contrasena": contrasena})
        assert_status(resp, 200, f"Login failed for {usuario}")
        return resp.json

    def _get_catalog(self) -> List[Dict[str, Any]]:
        resp = self.client.get("/api/v1/gestopago/productos")
        assert_status(resp, 200)
        return extract_products(resp.json)

    # =========================================================================
    # E1: Auth & Biometric Boundaries (6 tests)
    # =========================================================================

    @features("E1")
    def test_t2_e1_01_consecutive_failed_logins_lockout_ephemeral(self):
        """T2_E1_01: 5 failed password logins lock an ephemeral account (NEVER protected shared accounts)."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2elock")
        assert_not_protected_account(eph["usuario"])

        for i in range(5):
            r = self.client.post("/api/v1/auth/login", {"usuario": eph["usuario"], "contrasena": f"WrongPass#{i}"})
            assert_status(r, 401, f"Failed attempt {i + 1} must return 401")

        locked = self.client.post(
            "/api/v1/auth/login",
            {"usuario": eph["usuario"], "contrasena": eph["contrasena"]},
        )
        assert_status(locked, 401, "Locked ephemeral account must reject even valid password")

    @features("E1")
    def test_t2_e1_02_sqli_and_oversized_credentials(self):
        """T2_E1_02: SQLi strings and 5,000-char credentials return 400/401/413 without 500 or stack trace."""
        sqli = self.client.post(
            "/api/v1/auth/login",
            {"usuario": f"{self.config.username}' OR '1'='1", "contrasena": "x' OR '1'='1"},
        )
        assert_status(sqli, (400, 401))
        assert "Exception" not in sqli.text and "SQL" not in sqli.text

        oversized = "A" * 5000
        big = self.client.post("/api/v1/auth/login", {"usuario": oversized, "contrasena": oversized})
        assert_status(big, (400, 401, 413))

    @features("E1")
    def test_t2_e1_03_case_sensitive_username(self):
        """T2_E1_03: Username matching is strictly case-sensitive (single non-destructive check)."""
        upper_user = self.config.username.upper()
        if upper_user == self.config.username:
            raise SkipTest("Configured username has no lowercase characters")
        resp = self.client.post(
            "/api/v1/auth/login",
            {"usuario": upper_user, "contrasena": self.config.password},
        )
        assert_status(resp, 401, "Uppercase username must not match lowercase account")

    @features("E1")
    def test_t2_e1_04_biometric_token_length_bva_31_vs_32(self):
        """T2_E1_04: Biometric registration rejects 31-char token (400) and accepts 32-char token (200)."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2ebva")
        c = self.client.with_token(eph["token"])

        r31 = c.post("/api/v1/auth/biometria/registrar", {"tokenBiometrico": "a" * 31, "dispositivo": "Test"})
        assert_status(r31, 400, "31-char biometric token must return 400")

        r32 = c.post("/api/v1/auth/biometria/registrar", {"tokenBiometrico": "a" * 32, "dispositivo": "Test"})
        assert_status(r32, 200, "32-char biometric token must return 200")
        assert_field_equals(r32.json, "habilitada", True)

    @features("E1")
    def test_t2_e1_05_biometric_5_attempt_lockout(self):
        """T2_E1_05: 5 failed biometric login attempts lock biometric authentication (F3)."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2eblock")
        c = self.client.with_token(eph["token"])
        good_bio = "valid_bio_token_" + ("x" * 32)
        assert_status(c.post("/api/v1/auth/biometria/registrar", {"tokenBiometrico": good_bio}), 200)

        for i in range(5):
            r = self.client.post(
                "/api/v1/auth/login/biometrico",
                {"usuario": eph["usuario"], "tokenBiometrico": f"wrong_bio_{i}_" + ("z" * 32)},
            )
            assert_status(r, 401)

        locked = self.client.post(
            "/api/v1/auth/login/biometrico",
            {"usuario": eph["usuario"], "tokenBiometrico": good_bio},
        )
        assert_status(locked, 401, "Biometric login must be locked after 5 failed attempts")

    @features("E1")
    def test_t2_e1_06_biometric_revocation_blocks_login(self):
        """T2_E1_06: DELETE /biometria revokes token; estado=false and subsequent biometric login -> 401."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2ebrev")
        c = self.client.with_token(eph["token"])
        bio_tok = "rev_bio_token_" + ("y" * 32)
        assert_status(c.post("/api/v1/auth/biometria/registrar", {"tokenBiometrico": bio_tok}), 200)

        del_resp = c.delete("/api/v1/auth/biometria")
        assert_status(del_resp, 200)
        assert_field_equals(del_resp.json, "habilitada", False)

        estado = c.get("/api/v1/auth/biometria/estado")
        assert_status(estado, 200)
        assert_field_equals(estado.json, "habilitada", False)

        post_rev = self.client.post(
            "/api/v1/auth/login/biometrico",
            {"usuario": eph["usuario"], "tokenBiometrico": bio_tok},
        )
        assert_status(post_rev, 401, "Revoked biometric token must fail login with 401")

    # =========================================================================
    # E2: Catalog Boundaries (6 tests)
    # =========================================================================

    @features("E2")
    def test_t2_e2_01_burst_catalog_latency(self):
        """T2_E2_01: 5 rapid consecutive GET /productos calls succeed with avg latency < 2500ms."""
        latencies = []
        for _ in range(5):
            resp = self.client.get("/api/v1/gestopago/productos")
            assert_status(resp, 200)
            latencies.append(resp.latency_ms)
        avg_ms = sum(latencies) / len(latencies)
        assert avg_ms < 2500.0, f"Burst catalog average latency {avg_ms:.1f}ms >= 2500ms"

    @features("E2")
    def test_t2_e2_02_utf8_spanish_accents_preserved(self):
        """T2_E2_02: Catalog text preserves Spanish UTF-8 accents cleanly without mojibake."""
        products = self._get_catalog()
        corpus = " ".join(
            f"{product_name(p)} {p.get('legend') or ''}" for p in products
        )
        assert any(ch in corpus for ch in "áéíóúÁÉÍÓÚñÑ"), "Expected Spanish UTF-8 characters in catalog"
        assert "\ufffd" not in corpus and "Ã¡" not in corpus and "Ã©" not in corpus, "Mojibake detected"

    @features("E2")
    def test_t2_e2_03_unknown_query_params_ignored(self):
        """T2_E2_03: Unknown query parameters on /productos return 200 safely without 500."""
        resp = self.client.get("/api/v1/gestopago/productos?unknown=xyz&page=-1&admin=true")
        assert_status(resp, 200)
        assert len(extract_products(resp.json)) > 0

    @features("E2")
    def test_t2_e2_04_fixed_and_open_prices_and_show_ayuda(self):
        """T2_E2_04: Grouped catalog contains fixed-price (>0) and open-amount (==0) items plus boolean showAyuda."""
        resp = self.client.get("/api/v1/gestopago/productos/agrupados")
        assert_status(resp, 200)
        all_prods = [p for g in resp.json.get("grupos", []) for p in g.get("productos", [])]
        fixed = [p for p in all_prods if product_price(p) > 0]
        opened = [p for p in all_prods if product_price(p) == 0]
        assert len(fixed) > 0, "Must contain fixed-price products"
        assert len(opened) > 0, "Must contain open-amount products"
        assert all(isinstance(p.get("showAyuda"), bool) for p in all_prods[:20])

    @features("E2")
    def test_t2_e2_05_tipofront_4_and_5_classification(self):
        """T2_E2_05: Grouped catalog isolates tipoFront 4/5 and blocks them from payment validation."""
        resp = self.client.get("/api/v1/gestopago/productos/agrupados")
        assert_status(resp, 200)
        all_prods = [p for g in resp.json.get("grupos", []) for p in g.get("productos", [])]
        blocked = [p for p in all_prods if product_front_type(p) in ("4", "5")]
        if not blocked:
            raise SkipTest("No tipoFront 4/5 item present in live catalog")
        item = blocked[0]
        val = self.client.post(
            "/api/v1/pagos/validar-referencia",
            {"idServicio": product_service_id(item), "idProducto": product_id(item), "referencia": "5512345678"},
        )
        assert_status(val, 200)
        assert_field_equals(val.json, "esValida", False)

    @features("E2")
    def test_t2_e2_06_all_products_have_valid_service_id_and_logo_url(self):
        """T2_E2_06: Zero null/broken logoUrl or missing serviceId across entire catalog."""
        products = self._get_catalog()
        for p in products:
            assert str(p.get("serviceId", "")).strip().isdigit(), f"Invalid serviceId in {p}"
            logo = str(p.get("logoUrl", ""))
            assert logo.startswith("https://") and logo.endswith(".png"), f"Invalid logoUrl: {logo}"

    # =========================================================================
    # E3: Reference Validation Boundaries (6 tests)
    # =========================================================================

    @features("E3")
    def test_t2_e3_01_phone_length_bva_9_10_11(self):
        """T2_E3_01: Telcel tipoFront=1 rejects 9-digit and 11-digit phones, accepts 10 digits."""
        telcel = find_telcel_package(self._get_catalog(), 20.0)
        assert telcel is not None
        sid, pid = product_service_id(telcel), product_id(telcel)

        for bad_len in ("551234567", "55123456789"):
            r = self.client.post("/api/v1/pagos/validar-referencia", {"idServicio": sid, "idProducto": pid, "referencia": bad_len})
            assert_status(r, 200)
            assert_field_equals(r.json, "esValida", False, f"Phone {bad_len} must be invalid")

        ok = self.client.post("/api/v1/pagos/validar-referencia", {"idServicio": sid, "idProducto": pid, "referencia": "5512345678"})
        assert_status(ok, 200)
        assert_field_equals(ok.json, "esValida", True)

    @features("E3")
    def test_t2_e3_02_ift_phone_edge_prefixes_and_sequences(self):
        """T2_E3_02: IFT validation rejects 0/1 prefixes, all-equal digits, and sequential numbers; accepts 2512345678."""
        telcel = find_telcel_package(self._get_catalog(), 20.0)
        assert telcel is not None
        sid, pid = product_service_id(telcel), product_id(telcel)

        for bad in ("0512345678", "1512345678", "5555555555", "1234567890"):
            r = self.client.post("/api/v1/pagos/validar-referencia", {"idServicio": sid, "idProducto": pid, "referencia": bad})
            assert_status(r, 200)
            assert_field_equals(r.json, "esValida", False, f"IFT invalid phone {bad} must be rejected")

        good = self.client.post("/api/v1/pagos/validar-referencia", {"idServicio": sid, "idProducto": pid, "referencia": "2512345678"})
        assert_status(good, 200)
        assert_field_equals(good.json, "esValida", True)

    @features("E3")
    def test_t2_e3_03_barcode_type_b_length_bva_7_8_30_31(self):
        """T2_E3_03: tipoReferencia='b' rejects 7 and 31 digits, accepts 8 and 30 digits."""
        svc = find_by_front_and_ref(self._get_catalog(), "2", "b")
        if not svc:
            raise SkipTest("No tipoReferencia=b service available")
        sid, pid = product_service_id(svc), product_id(svc)

        for bad in ("1234567", "1" * 31):
            r = self.client.post("/api/v1/pagos/validar-referencia", {"idServicio": sid, "idProducto": pid, "referencia": bad})
            assert_status(r, 200)
            assert_field_equals(r.json, "esValida", False)

        for good in ("12345678", "1" * 30):
            r = self.client.post("/api/v1/pagos/validar-referencia", {"idServicio": sid, "idProducto": pid, "referencia": good})
            assert_status(r, 200)
            assert_field_equals(r.json, "esValida", True)

    @features("E3")
    def test_t2_e3_04_blocked_tipofront_4_and_5(self):
        """T2_E3_04: tipoFront 4/5 items always fail validar-referencia with info-only message."""
        blocked = find_blocked_front45(self._get_catalog())
        if not blocked:
            raise SkipTest("No tipoFront 4/5 item in catalog")
        r = self.client.post(
            "/api/v1/pagos/validar-referencia",
            {"idServicio": product_service_id(blocked), "idProducto": product_id(blocked), "referencia": "12345678901"},
        )
        assert_status(r, 200)
        assert_field_equals(r.json, "esValida", False)

    @features("E3")
    def test_t2_e3_05_luhn_verification_digit(self):
        """T2_E3_05: Product with hasVerificationDigit=true rejects corrupted digit and accepts valid Luhn Mod-10."""
        luhn_prod = find_luhn_product(self._get_catalog())
        if not luhn_prod:
            raise SkipTest("No hasVerificationDigit=true product in live catalog")
        valid_ref, bad_ref = generate_luhn_mod10_ref("411111111")
        sid, pid = product_service_id(luhn_prod), product_id(luhn_prod)

        r_bad = self.client.post("/api/v1/pagos/validar-referencia", {"idServicio": sid, "idProducto": pid, "referencia": bad_ref})
        assert_status(r_bad, 200)
        assert_field_equals(r_bad.json, "esValida", False, "Corrupted check digit must fail")

        r_ok = self.client.post("/api/v1/pagos/validar-referencia", {"idServicio": sid, "idProducto": pid, "referencia": valid_ref})
        assert_status(r_ok, 200)
        assert_field_equals(r_ok.json, "esValida", True, "Valid Luhn check digit must pass")

    @features("E3")
    def test_t2_e3_06_xss_and_blank_reference_rejected(self):
        """T2_E3_06: Blank string and XSS payload '<script>alert(1)</script>' are rejected safely."""
        cfe = find_cfe_service(self._get_catalog())
        assert cfe is not None
        sid, pid = product_service_id(cfe), product_id(cfe)
        for bad_ref in ("<script>alert(1)</script>", ""):
            r = self.client.post("/api/v1/pagos/validar-referencia", {"idServicio": sid, "idProducto": pid, "referencia": bad_ref})
            assert_status(r, (200, 400))
            if r.status_code == 200:
                assert_field_equals(r.json, "esValida", False)

    # =========================================================================
    # E4: 2FA & Payment Boundaries (6 tests)
    # =========================================================================

    @features("E4")
    def test_t2_e4_01_open_amount_boundaries_and_strict_json_type(self):
        """T2_E4_01: Payment rejects $9.99, $0, -$1, $50000.01, 3 decimals ($10.123), and string monto ('20.00')."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2eamt")
        c = self.client.with_token(eph["token"])
        cfe = find_cfe_service(self._get_catalog())
        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json) or "123456"

        for bad_monto in (9.99, 0.00, -1.00, 50000.01, 10.123, "20.00"):
            r = c.post(
                "/api/v1/pagos/servicios",
                {
                    "numeroCuenta": eph["numeroCuenta"],
                    "idServicio": product_service_id(cfe),
                    "idProducto": product_id(cfe),
                    "monto": bad_monto,
                    "referencia": "010101010101010101010101010101",
                    "codigo2FA": otp,
                },
            )
            assert_status(r, 400, f"Invalid monto {bad_monto!r} must return 400")

    @features("E4")
    def test_t2_e4_02_insufficient_balance_rejected(self):
        """T2_E4_02: Payment for $200.01 on $200.00 ephemeral account returns 400 and preserves balance."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2ebal")
        c = self.client.with_token(eph["token"])
        cfe = find_cfe_service(self._get_catalog())
        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json)
        if not otp and not self.config.is_mock:
            raise SkipTest("Valid OTP required in live mode to reach balance check")

        r = c.post(
            "/api/v1/pagos/servicios",
            {
                "numeroCuenta": eph["numeroCuenta"],
                "idServicio": product_service_id(cfe),
                "idProducto": product_id(cfe),
                "monto": 200.01,
                "referencia": "010101010101010101010101010101",
                "codigo2FA": otp,
            },
        )
        assert_status(r, 400)
        me = c.get("/api/v1/auth/me")
        assert abs(float(me.json["saldo"]) - 200.00) < 0.01

    @features("E4")
    def test_t2_e4_03_wrong_and_malformed_otp_rejected(self):
        """T2_E4_03: Malformed OTP ('ABCDEF', '12345') -> 400; wrong 6-digit OTP ('000000') -> 401."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2eotp")
        c = self.client.with_token(eph["token"])
        telcel = find_telcel_package(self._get_catalog(), 20.0)
        assert_status(c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]}), 200)

        for bad_fmt in ("ABCDEF", "12345"):
            r = c.post(
                "/api/v1/pagos/servicios",
                {
                    "numeroCuenta": eph["numeroCuenta"],
                    "idServicio": product_service_id(telcel),
                    "idProducto": product_id(telcel),
                    "monto": product_price(telcel),
                    "telefono": "5512345678",
                    "referencia": "5512345678",
                    "codigo2FA": bad_fmt,
                },
            )
            assert_status(r, 400, f"Malformed OTP {bad_fmt} must return 400")

        wrong = c.post(
            "/api/v1/pagos/servicios",
            {
                "numeroCuenta": eph["numeroCuenta"],
                "idServicio": product_service_id(telcel),
                "idProducto": product_id(telcel),
                "monto": product_price(telcel),
                "telefono": "5512345678",
                "referencia": "5512345678",
                "codigo2FA": "000000",
            },
        )
        assert_status(wrong, 401, "Wrong 6-digit OTP must return 401")

    @features("E4")
    def test_t2_e4_04_otp_single_use_and_duplicate_idempotency(self):
        """T2_E4_04: Consumed OTP replay returns 401; new OTP + identical payment within 2 min returns 409."""
        if not self.config.is_mock and (self.config.live_safe or not self.config.allow_live_payments):
            raise SkipTest("Skipped live payment mutation under --live-safe")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2eidm")
        c = self.client.with_token(eph["token"])
        telcel = find_telcel_package(self._get_catalog(), 20.0)
        phone = generate_valid_ift_phone()

        gen1 = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp1 = resolve_otp(c, eph["numeroCuenta"], self.config, gen1.json)
        if not otp1:
            raise SkipTest("No OTP available")

        payload = {
            "numeroCuenta": eph["numeroCuenta"],
            "idServicio": product_service_id(telcel),
            "idProducto": product_id(telcel),
            "monto": product_price(telcel),
            "telefono": phone,
            "referencia": phone,
            "codigo2FA": otp1,
        }
        assert_status(c.post("/api/v1/pagos/servicios", payload), 200)

        # Replay same OTP -> 401
        replay_otp = c.post("/api/v1/pagos/servicios", payload)
        assert_status(replay_otp, 401, "Reused OTP must return 401")

        # Generate fresh OTP and submit identical payment within 2 min -> 409 Conflict
        gen2 = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp2 = resolve_otp(c, eph["numeroCuenta"], self.config, gen2.json)
        if otp2:
            payload["codigo2FA"] = otp2
            dup = c.post("/api/v1/pagos/servicios", payload)
            assert_status(dup, 409, "Duplicate payment within 2-min window must return 409")

    @features("E4")
    def test_t2_e4_05_fixed_price_anti_tamper(self):
        """T2_E4_05: Tampering monto=1.00 on a fixed-price $20.00 Telcel package returns 400."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2etmp")
        c = self.client.with_token(eph["token"])
        telcel = find_telcel_package(self._get_catalog(), 20.0)
        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json)
        if not otp and not self.config.is_mock:
            raise SkipTest("OTP required in live mode")

        r = c.post(
            "/api/v1/pagos/servicios",
            {
                "numeroCuenta": eph["numeroCuenta"],
                "idServicio": product_service_id(telcel),
                "idProducto": product_id(telcel),
                "monto": 1.00,
                "telefono": "5512345678",
                "referencia": "5512345678",
                "codigo2FA": otp,
            },
        )
        assert_status(r, 400, "Fixed-price anti-tampering must return 400")

    @features("E4")
    def test_t2_e4_06_server_side_reference_gating_on_pay(self):
        """T2_E4_06: /pagos/servicios rejects blocked tipoFront 4/5 and invalid references server-side (400)."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2egate")
        c = self.client.with_token(eph["token"])
        catalog = self._get_catalog()
        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json)
        if not otp and not self.config.is_mock:
            raise SkipTest("OTP required in live mode")

        blocked = find_blocked_front45(catalog)
        if blocked:
            r_blk = c.post(
                "/api/v1/pagos/servicios",
                {
                    "numeroCuenta": eph["numeroCuenta"],
                    "idServicio": product_service_id(blocked),
                    "idProducto": product_id(blocked),
                    "monto": 20.00,
                    "referencia": "12345678901",
                    "codigo2FA": otp,
                },
            )
            assert_status(r_blk, 400, "Blocked tipoFront 4/5 payment must return 400")

        telcel = find_telcel_package(catalog, 20.0)
        r_badref = c.post(
            "/api/v1/pagos/servicios",
            {
                "numeroCuenta": eph["numeroCuenta"],
                "idServicio": product_service_id(telcel),
                "idProducto": product_id(telcel),
                "monto": product_price(telcel),
                "telefono": "5512345",
                "referencia": "5512345",
                "codigo2FA": otp,
            },
        )
        assert_status(r_badref, 400, "Invalid reference passed straight to /servicios must return 400")

    # =========================================================================
    # E5: History & Receipt Boundaries (6 tests)
    # =========================================================================

    @features("E5")
    def test_t2_e5_01_history_account_number_bva(self):
        """T2_E5_01: /pagos/historial rejects 15-digit, 17-digit, and alphanumeric account numbers (400)."""
        auth = self._login(self.config.username, self.config.password)
        c = self.client.with_token(auth["token"])
        for bad_cta in ("1" * 15, "1" * 17, "123456789012345A"):
            r = c.post("/api/v1/pagos/historial", {"numeroCuenta": bad_cta})
            assert_status(r, 400, f"Malformed account {bad_cta} must return 400")

    @features("E5")
    def test_t2_e5_02_fresh_user_empty_history(self):
        """T2_E5_02: Newly registered user has totalMovimientos=0 and pagos=[]."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2ehist0")
        c = self.client.with_token(eph["token"])
        r = c.post("/api/v1/pagos/historial", {"numeroCuenta": eph["numeroCuenta"]})
        assert_status(r, 200)
        assert_field_equals(r.json, "totalMovimientos", 0)
        assert_field_equals(r.json, "pagos", [])

    @features("E5")
    def test_t2_e5_03_resend_receipt_blocks_placeholder_and_malformed_email(self):
        """T2_E5_03: /comprobante/reenviar rejects @example.com placeholder and malformed email (400)."""
        auth = self._login(self.config.username, self.config.password)
        c = self.client.with_token(auth["token"])
        hist = c.post("/api/v1/pagos/historial", {"numeroCuenta": auth["numeroCuenta"]})
        pagos = hist.json.get("pagos", [])
        folio = pagos[0]["folioTicket"] if pagos else "FT-20261119-INIT1"

        for bad_mail in ("bad@example.com", "not-an-email"):
            r = c.post(
                "/api/v1/pagos/comprobante/reenviar",
                {"numeroCuenta": auth["numeroCuenta"], "folioTicket": folio, "correoDestino": bad_mail},
            )
            assert_status(r, 400, f"Resend to {bad_mail} must return 400")

    @features("E5")
    def test_t2_e5_04_pdf_nonexistent_folio_returns_404_or_400(self):
        """T2_E5_04: GET /comprobante/FT-99999999-NONE/pdf rejects unknown folio safely (404/400)."""
        auth = self._login(self.config.username, self.config.password)
        c = self.client.with_token(auth["token"])
        r = c.get("/api/v1/pagos/comprobante/FT-99999999-NONE/pdf")
        assert_status(r, (400, 404))

    @features("E5")
    def test_t2_e5_05_history_ignores_extra_json_fields(self):
        """T2_E5_05: /pagos/historial safely ignores unexpected JSON fields without exposing other data."""
        auth = self._login(self.config.username, self.config.password)
        c = self.client.with_token(auth["token"])
        r = c.post(
            "/api/v1/pagos/historial",
            {"numeroCuenta": auth["numeroCuenta"], "admin": True, "injected": "<script>alert(1)</script>"},
        )
        assert_status(r, 200)
        assert_field_equals(r.json, "numeroCuenta", auth["numeroCuenta"])

    @features("E5")
    def test_t2_e5_06_resend_receipt_with_valid_override_email(self):
        """T2_E5_06: /comprobante/reenviar succeeds with valid non-placeholder override email."""
        auth = self._login(self.config.username, self.config.password)
        c = self.client.with_token(auth["token"])
        hist = c.post("/api/v1/pagos/historial", {"numeroCuenta": auth["numeroCuenta"]})
        pagos = hist.json.get("pagos", [])
        if not pagos:
            raise SkipTest("No folio available for resend override test")
        override = f"audit.receipt@{self.config.test_email_domain}"
        r = c.post(
            "/api/v1/pagos/comprobante/reenviar",
            {
                "numeroCuenta": auth["numeroCuenta"],
                "folioTicket": pagos[0]["folioTicket"],
                "correoDestino": override,
            },
        )
        assert_status(r, 200)
        assert_field_equals(r.json, "enviado", True)

    # =========================================================================
    # E6: Account Lifecycle Boundaries (6 tests)
    # =========================================================================

    @features("E6")
    def test_t2_e6_01_register_age_boundaries_17_18_111_future(self):
        """T2_E6_01: /auth/register rejects age 17, age 111, future date, bad format; accepts exact 18th birthday."""
        today = date.today()
        # Exact 18th birthday today
        try:
            dob_18 = date(today.year - 18, today.month, today.day).isoformat()
        except ValueError:
            dob_18 = date(today.year - 18, today.month, 28).isoformat()
        # 17 years old (born 17 years ago + 1 day if needed, use year-17)
        dob_17 = f"{today.year - 17}-01-01"
        if date(today.year - 17, 1, 1) <= today:
            # Make sure strictly < 18: born (today.year - 18) on Dec 31 if today < Dec 31, else year - 17
            dob_17 = f"{today.year - 17}-06-15"
        dob_111 = f"{today.year - 111}-01-01"
        dob_future = f"{today.year + 1}-01-01"

        for bad_dob in (dob_17, dob_111, dob_future, "15-05-1995"):
            payload = build_ephemeral_user_payload(fecha_nacimiento=bad_dob, email_domain=self.config.test_email_domain)
            r = self.client.post("/api/v1/auth/register", payload)
            assert_status(r, 400, f"DOB {bad_dob} must return 400")

        ok_payload = build_ephemeral_user_payload(fecha_nacimiento=dob_18, email_domain=self.config.test_email_domain)
        r_ok = self.client.post("/api/v1/auth/register", ok_payload)
        assert_status(r_ok, 201, f"Exact 18th birthday {dob_18} must be accepted (201)")

    @features("E6")
    def test_t2_e6_02_register_blocks_placeholder_emails(self):
        """T2_E6_02: /auth/register rejects @example.com, @ejemplo.com, @test.com, and .local emails."""
        for dom in ("example.com", "ejemplo.com", "test.com", "host.local"):
            payload = build_ephemeral_user_payload(email_override=f"user.e2e@{dom}")
            r = self.client.post("/api/v1/auth/register", payload)
            assert_status(r, 400, f"Placeholder email domain {dom} must return 400")

    @features("E6")
    def test_t2_e6_03_register_rejects_weak_password_bad_name_and_bad_phone(self):
        """T2_E6_03: /auth/register rejects weak passwords, numeric names ('Juan123'), and invalid phone."""
        for weak in ("short1A", "alllowercase1", "ALLUPPERCASE1", "NoDigitsPass"):
            p = build_ephemeral_user_payload(password=weak, email_domain=self.config.test_email_domain)
            assert_status(self.client.post("/api/v1/auth/register", p), 400, f"Weak password {weak} must return 400")

        bad_name = build_ephemeral_user_payload(email_domain=self.config.test_email_domain)
        bad_name["nombre"] = "Juan123"
        assert_status(self.client.post("/api/v1/auth/register", bad_name), 400)

        bad_phone = build_ephemeral_user_payload(email_domain=self.config.test_email_domain)
        bad_phone["telefonoMovil"] = "0512345678"
        assert_status(self.client.post("/api/v1/auth/register", bad_phone), 400)

    @features("E6")
    def test_t2_e6_04_register_duplicate_email_conflict(self):
        """T2_E6_04: /auth/register with an already-registered email returns 409 Conflict."""
        eph1 = register_ephemeral_user(self.client, self.config, prefix="e2edup")
        dup_payload = build_ephemeral_user_payload(
            email_override=eph1["email"],
            email_domain=self.config.test_email_domain,
        )
        r = self.client.post("/api/v1/auth/register", dup_payload)
        assert_status(r, 409, "Duplicate email registration must return 409 Conflict")

    @features("E6")
    def test_t2_e6_05_change_password_validation_boundaries(self):
        """T2_E6_05: PUT /me/contrasena rejects wrong current (401), weak new (400), and same password (400)."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2epwb")
        c = self.client.with_token(eph["token"])

        r_wrong = c.put(
            "/api/v1/auth/me/contrasena",
            {"contrasenaActual": "WrongCurrent#1", "contrasenaNueva": "ValidNew#2026"},
        )
        assert_status(r_wrong, 401)

        r_weak = c.put(
            "/api/v1/auth/me/contrasena",
            {"contrasenaActual": eph["contrasena"], "contrasenaNueva": "weak"},
        )
        assert_status(r_weak, 400)

        r_same = c.put(
            "/api/v1/auth/me/contrasena",
            {"contrasenaActual": eph["contrasena"], "contrasenaNueva": eph["contrasena"]},
        )
        assert_status(r_same, 400)

    @features("E6")
    def test_t2_e6_06_abonar_amount_boundaries_and_strict_type(self):
        """T2_E6_06: POST /me/abonar rejects 0.99, 0, -5, 50000.01, and string '100.00' (400); accepts 1.00 and 50000.00 (200)."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2eabnb")
        c = self.client.with_token(eph["token"])

        for bad in (0.99, 0.00, -5.00, 50000.01, "100.00"):
            r = c.post("/api/v1/auth/me/abonar", {"monto": bad, "concepto": "Test BVA"})
            assert_status(r, 400, f"Invalid abono monto {bad!r} must return 400")

        r_min = c.post("/api/v1/auth/me/abonar", {"monto": 1.00, "concepto": "Min BVA"})
        assert_status(r_min, 200)
        assert abs(float(r_min.json["saldo"]) - 201.00) < 0.01

        r_max = c.post("/api/v1/auth/me/abonar", {"monto": 50000.00, "concepto": "Max BVA"})
        assert_status(r_max, 200)
        assert abs(float(r_max.json["saldo"]) - 50201.00) < 0.01

    # =========================================================================
    # E7: Negative Security, Auth & Cross-Account IDOR (6 tests)
    # =========================================================================

    @features("E7")
    def test_t2_e7_01_unauthenticated_usuario_query_bypass_blocked(self):
        """T2_E7_01: ?usuario= query param bypass returns 401 across /me, /me/perfil, and /me/abonar."""
        anon = self.client.with_token(None)
        u = self.config.username
        assert_status(anon.get(f"/api/v1/auth/me?usuario={u}"), 401)
        assert_status(
            anon.put(f"/api/v1/auth/me/perfil?usuario={u}", {"correo": "x@bancodigital-e2e.mx", "telefonoMovil": "5512345678"}),
            401,
        )
        assert_status(anon.post(f"/api/v1/auth/me/abonar?usuario={u}", {"monto": 100.0}), 401)

    @features("E7")
    def test_t2_e7_02_authenticated_ignores_usuario_query_param(self):
        """T2_E7_02: User B calling GET /me?usuario=<UserA> returns User B ONLY, never User A."""
        user_b = self._login(self.config.secondary_username, self.config.secondary_password)
        c_b = self.client.with_token(user_b["token"])
        r = c_b.get(f"/api/v1/auth/me?usuario={self.config.username}")
        if r.status_code == 200:
            assert_field_equals(r.json, "usuario", self.config.secondary_username)
        else:
            assert_status(r, 403)

    @features("E7")
    def test_t2_e7_03_cross_account_idor_403_sweep(self):
        """T2_E7_03: User A token targeting User B's numeroCuenta returns 403 Forbidden on all /pagos endpoints."""
        user_a = self._login(self.config.username, self.config.password)
        user_b = self._login(self.config.secondary_username, self.config.secondary_password)
        c_a = self.client.with_token(user_a["token"])
        cta_b = user_b["numeroCuenta"]

        assert_status(c_a.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": cta_b}), 403, "IDOR 2fa/generar")
        assert_status(
            c_a.post(
                "/api/v1/pagos/servicios",
                {
                    "numeroCuenta": cta_b,
                    "idServicio": 133,
                    "idProducto": 403,
                    "monto": 20.0,
                    "referencia": "5512345678",
                    "codigo2FA": "123456",
                },
            ),
            403,
            "IDOR pagos/servicios",
        )
        assert_status(c_a.post("/api/v1/pagos/historial", {"numeroCuenta": cta_b}), 403, "IDOR pagos/historial")
        assert_status(
            c_a.post("/api/v1/pagos/comprobante/reenviar", {"numeroCuenta": cta_b, "folioTicket": "FT-20261119-SEC01"}),
            403,
            "IDOR comprobante/reenviar",
        )

    @features("E7")
    def test_t2_e7_04_cross_account_pdf_idor_403(self):
        """T2_E7_04: User A token requesting User B's PDF receipt returns 403 Forbidden."""
        user_a = self._login(self.config.username, self.config.password)
        user_b = self._login(self.config.secondary_username, self.config.secondary_password)
        c_a = self.client.with_token(user_a["token"])
        c_b = self.client.with_token(user_b["token"])

        hist_b = c_b.post("/api/v1/pagos/historial", {"numeroCuenta": user_b["numeroCuenta"]})
        assert_status(hist_b, 200)
        pagos_b = hist_b.json.get("pagos", [])
        if not pagos_b:
            raise SkipTest("User B has no transactions to test PDF IDOR")
        folio_b = pagos_b[0]["folioTicket"]

        idor_pdf = c_a.get(f"/api/v1/pagos/comprobante/{folio_b}/pdf")
        assert_status(idor_pdf, 403, "Cross-account PDF download must return 403 Forbidden")

    @features("E7")
    def test_t2_e7_05_forged_and_malformed_auth_headers_return_401(self):
        """T2_E7_05: Malformed, Basic, empty, and forged Bearer tokens all return 401 Unauthorized."""
        anon = self.client.with_token(None)
        for bad_hdr in ("Bearer invalid-forged-token", "Basic dXNlcjpwYXNz", "Bearer ", "Token abc"):
            r = anon.get("/api/v1/auth/me", headers={"Authorization": bad_hdr})
            assert_status(r, 401, f"Auth header {bad_hdr!r} must return 401")

    @features("E7")
    def test_t2_e7_06_cors_blocks_evil_origin(self):
        """T2_E7_06: OPTIONS preflight from https://evil.attacker.com does NOT echo evil origin or wildcard *."""
        r = self.client.options(
            "/api/v1/auth/login",
            headers={"Origin": "https://evil.attacker.com"},
        )
        acao = r.header("Access-Control-Allow-Origin", "")
        assert acao not in ("https://evil.attacker.com", "*"), f"Disallowed CORS origin echoed: {acao!r}"
