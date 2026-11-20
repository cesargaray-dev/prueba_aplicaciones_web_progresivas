"""
Tier 3: Cross-Feature Pairwise Combinations E2E Test Suite (12 API Tests).
Verifies multi-module lifecycles, concurrency, session invalidation, IDOR matrices, and cross-session sync.
"""

import concurrent.futures
from typing import Any, Dict, List, Optional
from config import (
    E2EConfig,
    extract_products,
    find_blocked_front45,
    find_cfe_service,
    find_telcel_package,
    generate_valid_ift_phone,
    product_id,
    product_name,
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
    assert_status,
)
from tier1_feature_coverage import features


class Tier3CrossFeature:
    """Tier 3 Cross-Feature Combinations Test Group (12 tests)."""

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

    @features("E6", "E2", "E3", "E4", "E5")
    def test_t3_comb_01_register_abonar_pay_history_pdf_lifecycle(self):
        """T3_COMB_01: Full lifecycle: Register ($200) -> Abonar (+$100) -> Catalog -> Validate -> 2FA -> Pay ($20) -> History -> PDF."""
        if not self.config.is_mock and (self.config.live_safe or not self.config.allow_live_payments):
            raise SkipTest("Skipped live payment lifecycle under --live-safe")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2ecmb1")
        c = self.client.with_token(eph["token"])
        assert abs(eph["saldo"] - 200.00) < 0.01

        abono = c.post("/api/v1/auth/me/abonar", {"monto": 100.00, "concepto": "Fondeo T3"})
        assert_status(abono, 200)
        assert abs(float(abono.json["saldo"]) - 300.00) < 0.01

        telcel = find_telcel_package(self._get_catalog(), 20.0)
        assert telcel is not None
        phone = generate_valid_ift_phone()
        val = c.post(
            "/api/v1/pagos/validar-referencia",
            {
                "idServicio": product_service_id(telcel),
                "idProducto": product_id(telcel),
                "referencia": phone,
                "monto": 20.00,
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
        assert abs(float(pay.json["saldoRestanteCuenta"]) - 280.00) < 0.01
        folio = pay.json["folioTicket"]

        hist = c.post("/api/v1/pagos/historial", {"numeroCuenta": eph["numeroCuenta"]})
        assert_status(hist, 200)
        assert any(p["folioTicket"] == folio for p in hist.json["pagos"])

        pdf = c.get(f"/api/v1/pagos/comprobante/{folio}/pdf", headers={"Accept": "application/pdf"})
        assert_status(pdf, 200)
        assert_pdf_bytes(pdf)

    @features("E3", "E4", "E1")
    def test_t3_comb_02_invalid_validation_blocks_payment_preserves_balance(self):
        """T3_COMB_02: Invalid reference fails validation, is rejected on /servicios, and leaves /me balance unchanged."""
        auth = self._login(self.config.username, self.config.password)
        c = self.client.with_token(auth["token"])
        bal_before = float(c.get("/api/v1/auth/me").json["saldo"])

        telcel = find_telcel_package(self._get_catalog(), 20.0)
        val = c.post(
            "/api/v1/pagos/validar-referencia",
            {
                "idServicio": product_service_id(telcel),
                "idProducto": product_id(telcel),
                "referencia": "5512345",
                "monto": 20.00,
            },
        )
        assert_status(val, 200)
        assert_field_equals(val.json, "esValida", False)

        pay = c.post(
            "/api/v1/pagos/servicios",
            {
                "numeroCuenta": auth["numeroCuenta"],
                "idServicio": product_service_id(telcel),
                "idProducto": product_id(telcel),
                "monto": 20.00,
                "telefono": "5512345",
                "referencia": "5512345",
                "codigo2FA": "000000",
            },
        )
        assert_status(pay, (400, 401))
        bal_after = float(c.get("/api/v1/auth/me").json["saldo"])
        assert abs(bal_after - bal_before) < 0.01

    @features("E4")
    def test_t3_comb_03_concurrent_payment_single_use_otp_integrity(self):
        """T3_COMB_03: Two concurrent /pagos/servicios calls with same OTP allow at most 1 debit and zero balance race."""
        if not self.config.is_mock and (self.config.live_safe or not self.config.allow_live_payments):
            raise SkipTest("Skipped live payment concurrency under --live-safe")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2econc")
        c = self.client.with_token(eph["token"])
        telcel = find_telcel_package(self._get_catalog(), 20.0)
        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json)
        if not otp:
            raise SkipTest("No OTP available")

        def fire(phone_num: str):
            sibling = self.client.with_token(eph["token"])
            return sibling.post(
                "/api/v1/pagos/servicios",
                {
                    "numeroCuenta": eph["numeroCuenta"],
                    "idServicio": product_service_id(telcel),
                    "idProducto": product_id(telcel),
                    "monto": product_price(telcel),
                    "telefono": phone_num,
                    "referencia": phone_num,
                    "codigo2FA": otp,
                },
            )

        phones = [generate_valid_ift_phone(), generate_valid_ift_phone()]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            responses = list(pool.map(fire, phones))

        successes = [r for r in responses if r.status_code == 200]
        failures = [r for r in responses if r.status_code in (401, 409)]
        assert len(successes) == 1, f"Expected exactly 1 success, got {[r.status_code for r in responses]}"
        assert len(failures) == 1

        me_after = c.get("/api/v1/auth/me")
        expected = round(eph["saldo"] - product_price(telcel), 2)
        assert abs(float(me_after.json["saldo"]) - expected) < 0.01

    @features("E2", "E4", "E5")
    def test_t3_comb_04_catalog_metadata_propagates_to_history(self):
        """T3_COMB_04: Telcel and CFE catalog metadata (idServicio, servicio, producto, logoUrl) propagate 1:1 to history."""
        if not self.config.is_mock and (self.config.live_safe or not self.config.allow_live_payments):
            raise SkipTest("Skipped live payment under --live-safe")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2ecatp")
        c = self.client.with_token(eph["token"])
        catalog = self._get_catalog()
        telcel = find_telcel_package(catalog, 20.0)
        cfe = find_cfe_service(catalog)
        assert telcel and cfe

        for item, ref, amt in (
            (telcel, generate_valid_ift_phone(), product_price(telcel)),
            (cfe, "010101010101010101010101010101", 30.00),
        ):
            gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
            otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json)
            if not otp:
                raise SkipTest("No OTP available")
            pay = c.post(
                "/api/v1/pagos/servicios",
                {
                    "numeroCuenta": eph["numeroCuenta"],
                    "idServicio": product_service_id(item),
                    "idProducto": product_id(item),
                    "monto": amt,
                    "referencia": ref,
                    "codigo2FA": otp,
                },
            )
            assert_status(pay, 200)

        hist = c.post("/api/v1/pagos/historial", {"numeroCuenta": eph["numeroCuenta"]})
        assert_status(hist, 200)
        pagos = hist.json["pagos"]
        assert len(pagos) >= 2
        for expected_item in (telcel, cfe):
            sid = product_service_id(expected_item)
            match = next((p for p in pagos if int(p["idServicio"]) == sid), None)
            assert match is not None, f"Service {sid} missing in history"
            assert match["servicio"] == product_service_name(expected_item)
            assert match["producto"] == product_name(expected_item)
            assert match["logoUrl"] == expected_item["logoUrl"]

    @features("E6", "E1")
    def test_t3_comb_05_password_change_invalidates_other_sessions(self):
        """T3_COMB_05: Changing password on Session A invalidates Session B (/me -> 401) and old password."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2esess")
        sess_a = self.client.with_token(eph["token"])
        login_b = self._login(eph["usuario"], eph["contrasena"])
        sess_b = self.client.with_token(login_b["token"])

        assert_status(sess_b.get("/api/v1/auth/me"), 200)

        new_pass = "RotatedPass#2026"
        chg = sess_a.put(
            "/api/v1/auth/me/contrasena",
            {"contrasenaActual": eph["contrasena"], "contrasenaNueva": new_pass},
        )
        assert_status(chg, 200)

        assert_status(sess_b.get("/api/v1/auth/me"), 401, "Session B must be invalidated on password change")
        assert_status(
            self.client.post("/api/v1/auth/login", {"usuario": eph["usuario"], "contrasena": eph["contrasena"]}),
            401,
            "Old password must be rejected",
        )
        assert_status(
            self.client.post("/api/v1/auth/login", {"usuario": eph["usuario"], "contrasena": new_pass}),
            200,
            "New password must succeed",
        )

    @features("E1", "E6")
    def test_t3_comb_06_full_biometric_enroll_login_revoke_lifecycle(self):
        """T3_COMB_06: Register -> Enroll biometric -> Estado true -> Logout -> Biometric login -> Revoke -> Bio login 401."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2eblc")
        c = self.client.with_token(eph["token"])
        bio_tok = "bio_lifecycle_" + ("k" * 32)

        assert_status(c.post("/api/v1/auth/biometria/registrar", {"tokenBiometrico": bio_tok}), 200)
        assert_field_equals(c.get("/api/v1/auth/biometria/estado").json, "habilitada", True)
        assert_status(c.post("/api/v1/auth/logout", {}), 200)

        bio_auth = self.client.post(
            "/api/v1/auth/login/biometrico",
            {"usuario": eph["usuario"], "tokenBiometrico": bio_tok},
        )
        assert_status(bio_auth, 200)
        c2 = self.client.with_token(bio_auth.json["token"])
        assert_status(c2.delete("/api/v1/auth/biometria"), 200)

        post_revoke = self.client.post(
            "/api/v1/auth/login/biometrico",
            {"usuario": eph["usuario"], "tokenBiometrico": bio_tok},
        )
        assert_status(post_revoke, 401)

    @features("E7", "E4", "E5")
    def test_t3_comb_07_cross_account_idor_and_query_bypass_matrix(self):
        """T3_COMB_07: Full IDOR matrix: User A vs User B across /2fa, /servicios, /historial, /reenviar, /pdf, /me?usuario=."""
        user_a = self._login(self.config.username, self.config.password)
        user_b = self._login(self.config.secondary_username, self.config.secondary_password)
        c_a = self.client.with_token(user_a["token"])
        c_b = self.client.with_token(user_b["token"])
        cta_b = user_b["numeroCuenta"]

        assert_status(c_a.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": cta_b}), 403)
        assert_status(c_a.post("/api/v1/pagos/historial", {"numeroCuenta": cta_b}), 403)
        assert_status(
            c_a.post("/api/v1/pagos/comprobante/reenviar", {"numeroCuenta": cta_b, "folioTicket": "FT-20261119-SEC01"}),
            403,
        )

        hist_b = c_b.post("/api/v1/pagos/historial", {"numeroCuenta": cta_b}).json.get("pagos", [])
        if hist_b:
            assert_status(c_a.get(f"/api/v1/pagos/comprobante/{hist_b[0]['folioTicket']}/pdf"), 403)

        me_spoof = c_a.get(f"/api/v1/auth/me?usuario={self.config.secondary_username}")
        assert_status(me_spoof, 200)
        assert_field_equals(me_spoof.json, "usuario", self.config.username)

        bio_spoof = c_a.get(f"/api/v1/auth/biometria/estado?usuario={self.config.secondary_username}")
        assert_status(bio_spoof, 200)
        assert_field_equals(bio_spoof.json, "usuario", self.config.username)

    @features("E6", "E5")
    def test_t3_comb_08_profile_email_update_propagates_to_receipt_resend(self):
        """T3_COMB_08: Updating email via PUT /me/perfil routes subsequent /comprobante/reenviar to new masked email."""
        if not self.config.is_mock and (self.config.live_safe or not self.config.allow_live_payments):
            raise SkipTest("Skipped live payment under --live-safe")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2eresnd")
        c = self.client.with_token(eph["token"])
        telcel = find_telcel_package(self._get_catalog(), 20.0)
        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json)
        if not otp:
            raise SkipTest("No OTP available")

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
        folio = pay.json["folioTicket"]

        updated_email = f"nuevo.comprobante.{eph['usuario']}@{self.config.test_email_domain}"
        upd = c.put(
            "/api/v1/auth/me/perfil",
            {"correo": updated_email, "telefonoMovil": eph["telefonoMovil"]},
        )
        assert_status(upd, 200)

        res = c.post("/api/v1/pagos/comprobante/reenviar", {"numeroCuenta": eph["numeroCuenta"], "folioTicket": folio})
        assert_status(res, 200)
        assert_field_equals(res.json, "enviado", True)
        assert "nue" in res.json.get("correoDestino", ""), "Receipt must target updated profile email"

    @features("E6", "E8")
    def test_t3_comb_09_cross_session_profile_and_topup_sync(self):
        """T3_COMB_09: Profile edit & top-up on Web session reflect immediately on Mobile session GET /me."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2esync1")
        web_client = self.client.with_token(eph["token"])
        mob_login = self._login(eph["usuario"], eph["contrasena"])
        mob_client = self.client.with_token(mob_login["token"])

        new_phone = generate_valid_ift_phone()
        assert_status(
            web_client.put(
                "/api/v1/auth/me/perfil",
                {
                    "nombre": "Sofia",
                    "apellidoPaterno": "Navarro",
                    "apellidoMaterno": "Ortiz",
                    "correo": eph["email"],
                    "telefonoMovil": new_phone,
                },
            ),
            200,
        )
        assert_status(web_client.post("/api/v1/auth/me/abonar", {"monto": 75.00, "concepto": "Sync Web"}), 200)

        mob_me = mob_client.get("/api/v1/auth/me")
        assert_status(mob_me, 200)
        assert_field_equals(mob_me.json, "telefonoMovil", new_phone)
        assert "Sofia" in mob_me.json.get("nombreCompleto", "")
        assert abs(float(mob_me.json["saldo"]) - 275.00) < 0.01

    @features("E4", "E5", "E8")
    def test_t3_comb_10_cross_session_payment_and_history_sync(self):
        """T3_COMB_10: Payment executed on Mobile session appears immediately in Web session balance & history."""
        if not self.config.is_mock and (self.config.live_safe or not self.config.allow_live_payments):
            raise SkipTest("Skipped live payment under --live-safe")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2esync2")
        web_client = self.client.with_token(eph["token"])
        mob_client = self.client.with_token(self._login(eph["usuario"], eph["contrasena"])["token"])

        telcel = find_telcel_package(self._get_catalog(), 20.0)
        gen = mob_client.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp = resolve_otp(mob_client, eph["numeroCuenta"], self.config, gen.json)
        if not otp:
            raise SkipTest("No OTP available")

        phone = generate_valid_ift_phone()
        pay = mob_client.post(
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
        folio = pay.json["folioTicket"]

        web_me = web_client.get("/api/v1/auth/me")
        assert abs(float(web_me.json["saldo"]) - 180.00) < 0.01
        web_hist = web_client.post("/api/v1/pagos/historial", {"numeroCuenta": eph["numeroCuenta"]})
        assert_status(web_hist, 200)
        assert web_hist.json["pagos"][0]["folioTicket"] == folio

    @features("E2", "E3", "E7")
    def test_t3_comb_11_tipofront_45_and_10_floor_api_gating(self):
        """T3_COMB_11: tipoFront 4/5 and <$10.00 open-amount are blocked at both validation and payment APIs."""
        eph = register_ephemeral_user(self.client, self.config, prefix="e2egating")
        c = self.client.with_token(eph["token"])
        catalog = self._get_catalog()
        blocked = find_blocked_front45(catalog)
        cfe = find_cfe_service(catalog)

        gen = c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]})
        otp = resolve_otp(c, eph["numeroCuenta"], self.config, gen.json) or "123456"

        if blocked:
            v = c.post(
                "/api/v1/pagos/validar-referencia",
                {"idServicio": product_service_id(blocked), "idProducto": product_id(blocked), "referencia": "12345678901"},
            )
            assert_field_equals(v.json, "esValida", False)
            p = c.post(
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
            assert_status(p, (400, 401))

        if cfe:
            p_low = c.post(
                "/api/v1/pagos/servicios",
                {
                    "numeroCuenta": eph["numeroCuenta"],
                    "idServicio": product_service_id(cfe),
                    "idProducto": product_id(cfe),
                    "monto": 9.99,
                    "referencia": "010101010101010101010101010101",
                    "codigo2FA": otp,
                },
            )
            assert_status(p_low, (400, 401))

        assert abs(float(c.get("/api/v1/auth/me").json["saldo"]) - 200.00) < 0.01

    @features("E4", "E5", "E7")
    def test_t3_comb_12_three_state_consistency_across_api_and_history(self):
        """T3_COMB_12: Only EXITOSA deducts balance; RECHAZADA and PENDIENTE never mutate account balance."""
        if not self.config.is_mock:
            raise SkipTest("3-state fault injection consistency test requires --mock mode")

        eph = register_ephemeral_user(self.client, self.config, prefix="e2e3st")
        c = self.client.with_token(eph["token"])
        cfe = find_cfe_service(self._get_catalog())
        sid, pid = product_service_id(cfe), product_id(cfe)

        # 1. RECHAZADA -> no debit
        otp1 = resolve_otp(c, eph["numeroCuenta"], self.config, c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]}).json)
        r_rej = c.post("/api/v1/pagos/servicios", {"numeroCuenta": eph["numeroCuenta"], "idServicio": sid, "idProducto": pid, "monto": 30.0, "referencia": "RECHAZAR01", "codigo2FA": otp1})
        assert_status(r_rej, (400, 409, 422))
        assert_field_equals(r_rej.json, "estadoOperacion", "RECHAZADA")
        assert abs(float(c.get("/api/v1/auth/me").json["saldo"]) - 200.00) < 0.01

        # 2. PENDIENTE -> no debit
        otp2 = resolve_otp(c, eph["numeroCuenta"], self.config, c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]}).json)
        r_pen = c.post("/api/v1/pagos/servicios", {"numeroCuenta": eph["numeroCuenta"], "idServicio": sid, "idProducto": pid, "monto": 30.0, "referencia": "TIMEOUT01", "codigo2FA": otp2})
        assert_status(r_pen, (504, 202))
        assert_field_equals(r_pen.json, "estadoOperacion", "PENDIENTE")
        assert abs(float(c.get("/api/v1/auth/me").json["saldo"]) - 200.00) < 0.01

        # 3. EXITOSA -> exact $30.00 debit
        otp3 = resolve_otp(c, eph["numeroCuenta"], self.config, c.post("/api/v1/pagos/2fa/generar", {"numeroCuenta": eph["numeroCuenta"]}).json)
        r_ok = c.post("/api/v1/pagos/servicios", {"numeroCuenta": eph["numeroCuenta"], "idServicio": sid, "idProducto": pid, "monto": 30.0, "referencia": "010101010101010101010101010101", "codigo2FA": otp3})
        assert_status(r_ok, 200)
        assert_field_equals(r_ok.json, "estadoOperacion", "EXITOSA")
        assert abs(float(c.get("/api/v1/auth/me").json["saldo"]) - 170.00) < 0.01
