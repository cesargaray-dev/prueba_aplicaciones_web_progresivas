"""
Tier 4: Real-World Application Scenarios E2E Test Suite (10 API Tests).
End-to-end user journeys covering all 10 Professor Scenarios + R1-R3 requirements.
"""

from typing import Any, Dict, List, Optional
from config import (
    E2EConfig,
    assert_not_protected_account,
    extract_products,
    find_cfe_service,
    find_telcel_package,
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
    assert_field_equals,
    assert_field_present,
    assert_is_none_or_absent,
    assert_pdf_bytes,
    assert_regex_match,
    assert_status,
)
from tier1_feature_coverage import features


class Tier4RealWorldScenarios:
    """Tier 4 Real-World Application Scenarios Test Group (10 scenarios)."""

    def __init__(self, client: E2EClient, config: Optional[E2EConfig] = None):
        self.client = client
        self.config = config or E2EConfig()

    def _get_catalog(self) -> List[Dict[str, Any]]:
        resp = self.client.get("/api/v1/gestopago/productos")
        assert_status(resp, 200)
        return extract_products(resp.json)

    def _login(self, usuario: str, contrasena: str) -> Dict[str, Any]:
        resp = self.client.post("/api/v1/auth/login", {"usuario": usuario, "contrasena": contrasena})
        assert_status(resp, 200)
        return resp.json

    @features("E1", "E2", "E3", "E4", "E5")
    def test_t4_scen_01_telcel_airtime_web_journey(self):
        """T4_SCEN_01: Telcel Airtime Journey (Prof. 1,3,4,5,7,8,9): Login -> Catalog/Logos -> Validate -> 2FA -> Pay -> History."""
        if not self.config.is_mock and (self.config.live_safe or not self.config.allow_live_payments):
            raise SkipTest("Skipped live payment journey under --live-safe")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2esc1")
        c = self.client.with_token(eph["token"])
        telcel = find_telcel_package(self._get_catalog(), 20.0)
        assert telcel is not None
        assert_regex_match(telcel["logoUrl"], r"^https://gestopago\.portalventas\.net/.+\.png$")

        phone = generate_valid_ift_phone()
        val = c.post(
            "/api/v1/pagos/validar-referencia",
            {
                "idServicio": product_service_id(telcel),
                "idProducto": product_id(telcel),
                "referencia": phone,
                "monto": product_price(telcel),
            },
        )
        assert_status(val, 200)
        assert_field_equals(val.json, "esValida", True)

        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        assert_status(gen, 200)
        assert_is_none_or_absent(gen.json, "codigo2FA")
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json)
        if not otp:
            raise SkipTest("No OTP available")

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
        assert_field_equals(pay.json, "estadoOperacion", "EXITOSA")
        folio = pay.json["folioTicket"]
        assert_regex_match(folio, r"^FT-\d{8,14}-[A-Z0-9]+$")
        assert abs(float(pay.json["saldoRestanteCuenta"]) - (eph["saldo"] - product_price(telcel))) < 0.01

        hist = c.post("/api/v1/pagos/historial", {"numeroCuenta": eph["numeroCuenta"]})
        assert_status(hist, 200)
        latest = hist.json["pagos"][0]
        assert_field_equals(latest, "folioTicket", folio)
        assert_field_equals(latest, "estado", "EXITOSA")

    @features("E1", "E2", "E3", "E4", "E5", "E8")
    def test_t4_scen_02_cfe_bill_payment_cross_platform_journey(self):
        """T4_SCEN_02: CFE Bill Payment & Cross-Platform Visibility (Prof. 2,3,4,5,7,8,9,10)."""
        if not self.config.is_mock and (self.config.live_safe or not self.config.allow_live_payments):
            raise SkipTest("Skipped live payment journey under --live-safe")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2esc2")
        mobile_sess = self.client.with_token(eph["token"])
        web_sess = self.client.with_token(self._login(eph["usuario"], eph["contrasena"])["token"])

        cfe = find_cfe_service(self._get_catalog())
        assert cfe is not None
        barcode = "010101010101010101010101010101"
        val = mobile_sess.post(
            "/api/v1/pagos/validar-referencia",
            {
                "idServicio": product_service_id(cfe),
                "idProducto": product_id(cfe),
                "referencia": barcode,
                "monto": 20.00,
            },
        )
        assert_status(val, 200)
        assert_field_equals(val.json, "esValida", True)

        gen = mobile_sess.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp = resolve_otp(mobile_sess, eph["numeroCuenta"], self.config, gen.json)
        if not otp:
            raise SkipTest("No OTP available")

        pay = mobile_sess.post(
            "/api/v1/pagos/servicios",
            {
                "numeroCuenta": eph["numeroCuenta"],
                "idServicio": product_service_id(cfe),
                "idProducto": product_id(cfe),
                "monto": 20.00,
                "referencia": barcode,
                "codigo2FA": otp,
            },
        )
        assert_status(pay, 200)
        assert_field_equals(pay.json, "estadoOperacion", "EXITOSA")
        folio = pay.json["folioTicket"]

        # Scenario 10: Query from second platform session (Web)
        web_hist = web_sess.post("/api/v1/pagos/historial", {"numeroCuenta": eph["numeroCuenta"]})
        assert_status(web_hist, 200)
        latest = web_hist.json["pagos"][0]
        assert_field_equals(latest, "folioTicket", folio)
        assert_field_equals(latest, "servicio", product_service_name(cfe))
        assert f"/{product_service_id(cfe)}.png" in latest["logoUrl"]

    @features("E3", "E4")
    def test_t4_scen_03_invalid_reference_and_insufficient_balance_journey(self):
        """T4_SCEN_03: Invalid 7d reference & insufficient balance ($250 on $200 account) rejected cleanly (Prof. 6,8)."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2esc3")
        c = self.client.with_token(eph["token"])
        catalog = self._get_catalog()
        telcel = find_telcel_package(catalog, 20.0)
        cfe = find_cfe_service(catalog)

        bad_val = c.post(
            "/api/v1/pagos/validar-referencia",
            {
                "idServicio": product_service_id(telcel),
                "idProducto": product_id(telcel),
                "referencia": "5512345",
                "monto": 20.00,
            },
        )
        assert_status(bad_val, 200)
        assert_field_equals(bad_val.json, "esValida", False)

        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json) or "123456"
        over_pay = c.post(
            "/api/v1/pagos/servicios",
            {
                "numeroCuenta": eph["numeroCuenta"],
                "idServicio": product_service_id(cfe),
                "idProducto": product_id(cfe),
                "monto": 250.00,
                "referencia": "010101010101010101010101010101",
                "codigo2FA": otp,
            },
        )
        assert_status(over_pay, (400, 401))
        assert abs(float(c.get("/api/v1/auth/me").json["saldo"]) - 200.00) < 0.01

    @features("E4")
    def test_t4_scen_04_timeout_pendiente_reconciliation_journey(self):
        """T4_SCEN_04: Provider timeout yields PENDIENTE with upc conciliation folio and preserves balance (Prof. 8)."""
        if not self.config.is_mock:
            raise SkipTest("Timeout PENDIENTE fault-injection journey requires --mock mode")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2esc4")
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
                "monto": 50.00,
                "referencia": "TIMEOUT_SCEN04",
                "codigo2FA": otp,
            },
        )
        assert_status(resp, (504, 202))
        assert_field_equals(resp.json, "estadoOperacion", "PENDIENTE")
        assert_field_present(resp.json, "upc")
        assert_field_present(resp.json, "folioTicket")
        assert abs(float(c.get("/api/v1/auth/me").json["saldo"]) - 200.00) < 0.01

    @features("E6", "E8")
    def test_t4_scen_05_commercial_onboarding_and_funding_journey(self):
        """T4_SCEN_05: Self-register ($200) -> Top-up $50 -> Verify $250.00 across two sessions (R2, R3)."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2esc5")
        assert abs(eph["saldo"] - 200.00) < 0.01

        sess1 = self.client.with_token(eph["token"])
        topup = sess1.post("/api/v1/auth/me/abonar", {"monto": 50.00, "concepto": "Recarga inicial"})
        assert_status(topup, 200)
        assert abs(float(topup.json["saldo"]) - 250.00) < 0.01

        sess2_login = self._login(eph["usuario"], eph["contrasena"])
        sess2 = self.client.with_token(sess2_login["token"])
        me2 = sess2.get("/api/v1/auth/me")
        assert_status(me2, 200)
        assert abs(float(me2.json["saldo"]) - 250.00) < 0.01

    @features("E6", "E1", "E8")
    def test_t4_scen_06_profile_sync_and_password_rotation_journey(self):
        """T4_SCEN_06: Edit profile on Web -> verify on Mobile -> rotate password -> old session 401 -> login new (R2, R3)."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2esc6")
        web_c = self.client.with_token(eph["token"])
        mob_c = self.client.with_token(self._login(eph["usuario"], eph["contrasena"])["token"])

        new_phone = generate_valid_ift_phone()
        assert_status(
            web_c.put(
                "/api/v1/auth/me/perfil",
                {
                    "nombre": "Valeria",
                    "apellidoPaterno": "Torres",
                    "apellidoMaterno": "Mora",
                    "correo": eph["email"],
                    "telefonoMovil": new_phone,
                },
            ),
            200,
        )
        mob_me = mob_c.get("/api/v1/auth/me")
        assert_status(mob_me, 200)
        assert_field_equals(mob_me.json, "telefonoMovil", new_phone)
        assert "Valeria" in mob_me.json["nombreCompleto"]

        new_pass = "FinalSecPass#2026"
        assert_status(
            web_c.put(
                "/api/v1/auth/me/contrasena",
                {"contrasenaActual": eph["contrasena"], "contrasenaNueva": new_pass},
            ),
            200,
        )
        assert_status(mob_c.get("/api/v1/auth/me"), 401)
        assert_status(self.client.post("/api/v1/auth/login", {"usuario": eph["usuario"], "contrasena": new_pass}), 200)

    @features("E1", "E6")
    def test_t4_scen_07_biometric_choice_and_revocation_journey(self):
        """T4_SCEN_07: Enroll biometric -> login biometric (200) -> revoke -> bio fails (401) -> password login works (Prof. 2)."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2esc7")
        c = self.client.with_token(eph["token"])
        bio_tok = "device_fingerprint_secret_" + ("m" * 32)

        assert_status(c.post("/api/v1/auth/biometria/registrar", {"tokenBiometrico": bio_tok, "dispositivo": "Galaxy S24"}), 200)
        bio_login = self.client.post("/api/v1/auth/login/biometrico", {"usuario": eph["usuario"], "tokenBiometrico": bio_tok})
        assert_status(bio_login, 200)

        c_bio = self.client.with_token(bio_login.json["token"])
        assert_status(c_bio.delete("/api/v1/auth/biometria"), 200)
        assert_status(
            self.client.post("/api/v1/auth/login/biometrico", {"usuario": eph["usuario"], "tokenBiometrico": bio_tok}),
            401,
        )
        assert_status(
            self.client.post("/api/v1/auth/login", {"usuario": eph["usuario"], "contrasena": eph["contrasena"]}),
            200,
        )

    @features("E5")
    def test_t4_scen_08_receipt_audit_pdf_and_brevo_resend_journey(self):
        """T4_SCEN_08: Locate EXITOSA folio in history -> download %PDF- -> resend via Brevo (R2, Prof. 9)."""
        auth = self._login(self.config.username, self.config.password)
        c = self.client.with_token(auth["token"])
        hist = c.post("/api/v1/pagos/historial", {"numeroCuenta": auth["numeroCuenta"]})
        assert_status(hist, 200)
        pagos = hist.json.get("pagos", [])
        if not pagos:
            raise SkipTest("No transactions in account history for receipt audit")
        folio = pagos[0]["folioTicket"]

        pdf = c.get(f"/api/v1/pagos/comprobante/{folio}/pdf", headers={"Accept": "application/pdf"})
        assert_status(pdf, 200)
        assert_pdf_bytes(pdf)

        resend = c.post(
            "/api/v1/pagos/comprobante/reenviar",
            {"numeroCuenta": auth["numeroCuenta"], "folioTicket": folio},
        )
        assert_status(resend, 200)
        assert_field_equals(resend.json, "enviado", True)

    @features("E1", "E4")
    def test_t4_scen_09_bruteforce_lockout_and_duplicate_replay_defense(self):
        """T4_SCEN_09: Brute-force locks ephemeral user (401); separate user blocks 2-min duplicate replay (409) (R1)."""
        # Part A: Ephemeral lockout
        lock_eph = register_ephemeral_user(self.client, self.config, prefix="e2esc9l")
        assert_not_protected_account(lock_eph["usuario"])
        for i in range(5):
            self.client.post("/api/v1/auth/login", {"usuario": lock_eph["usuario"], "contrasena": f"Bad#{i}"})
        assert_status(
            self.client.post("/api/v1/auth/login", {"usuario": lock_eph["usuario"], "contrasena": lock_eph["contrasena"]}),
            401,
        )

        # Part B: Duplicate replay 409
        if not self.config.is_mock and (self.config.live_safe or not self.config.allow_live_payments):
            return
        pay_eph = register_ephemeral_user(self.client, self.config, prefix="e2esc9p")
        c = self.client.with_token(pay_eph["token"])
        telcel = find_telcel_package(self._get_catalog(), 20.0)
        phone = generate_valid_ift_phone()
        otp1 = resolve_otp(c, pay_eph["numeroCuenta"], self.config, c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": pay_eph["numeroCuenta"]}).json)
        if not otp1:
            return
        payload = {
            "numeroCuenta": pay_eph["numeroCuenta"],
            "idServicio": product_service_id(telcel),
            "idProducto": product_id(telcel),
            "monto": product_price(telcel),
            "telefono": phone,
            "referencia": phone,
            "codigo2FA": otp1,
        }
        assert_status(c.post("/api/v1/pagos/servicios", payload), 200)
        otp2 = resolve_otp(c, pay_eph["numeroCuenta"], self.config, c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": pay_eph["numeroCuenta"]}).json)
        if otp2:
            payload["codigo2FA"] = otp2
            assert_status(c.post("/api/v1/pagos/servicios", payload), 409)

    @features("E7")
    def test_t4_scen_10_zero_trust_security_audit_journey(self):
        """T4_SCEN_10: Full zero-trust sweep: unauth 401, ?usuario= blocked, cross-account 403, /actuator/health UP, strict CORS (R1)."""
        anon = self.client.with_token(None)
        for path in ("/api/v1/auth/me", f"/api/v1/auth/me?usuario={self.config.username}", "/api/v1/clientes", "/api/v1/cuentas/activas"):
            assert_status(anon.get(path), 401, f"Unauthenticated GET {path} must return 401")
        assert_status(anon.post("/api/v1/pagos/historial", {"numeroCuenta": "1234567890123456"}), 401)
        assert_status(anon.post("/personas", {"nombre": "X"}), 401)

        health = anon.get("/actuator/health")
        assert_status(health, 200)
        assert_field_equals(health.json, "status", "UP")

        cors_bad = anon.options("/api/v1/auth/login", headers={"Origin": "https://evil.attacker.com"})
        assert cors_bad.header("Access-Control-Allow-Origin", "") not in ("https://evil.attacker.com", "*")

        user_a = self._login(self.config.username, self.config.password)
        user_b = self._login(self.config.secondary_username, self.config.secondary_password)
        c_a = self.client.with_token(user_a["token"])
        assert_status(c_a.post("/api/v1/pagos/historial", {"numeroCuenta": user_b["numeroCuenta"]}), 403)
        assert_status(c_a.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": user_b["numeroCuenta"]}), 403)
